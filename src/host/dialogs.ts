import * as vscode from 'vscode';
import { RequalityClient } from '../server/connection';

/** Handler for a named view or dialog the core asks for (IMessageConsumer.openView). */
export type ViewHandler = (project: string | null, args: string[]) => unknown | Promise<unknown>;

const viewHandlers = new Map<string, ViewHandler>();

/** Registers the extension side of a named view/dialog requested by the core. */
export function registerViewHandler(name: string, handler: ViewHandler): vscode.Disposable {
  if (viewHandlers.has(name)) {
    throw new Error(`Duplicate view handler ${name}`);
  }
  viewHandlers.set(name, handler);
  return new vscode.Disposable(() => viewHandlers.delete(name));
}

interface ShowMessageParams {
  severity: 'error' | 'warning' | 'info';
  title: string | null;
  message: string | null;
  detail: string | null;
  modal: boolean;
  items: string[] | null;
}

interface LongMessageParams {
  title: string | null;
  message: string | null;
  buttons: string[] | null;
}

interface InputBoxParams {
  session: string;
  title: string | null;
  prompt: string | null;
  value: string | null;
  validate: boolean;
}

export interface SelectNodeParams {
  project: string;
  title?: string;
  types?: string[];
  exclude?: string;
}

/** Joins the dialog title and message the way an SWT message box shows them. */
export function messageText(title: string | null, message: string | null): string {
  const t = (title ?? '').trim();
  const m = (message ?? '').trim();
  if (!t) {
    return m;
  }
  if (!m) {
    return t;
  }
  return `${t}: ${m}`;
}

const CLOSE_LABELS = new Set(['Cancel', 'No']);

/**
 * Shows a message with SWT-like buttons. "Cancel"/"No" are the close affordance, so a modal dialog does not get an
 * extra Cancel button and Escape returns them; an "OK"-only modal uses VS Code's own OK button.
 */
async function show(severity: string, text: string, options: vscode.MessageOptions, labels: string[]): Promise<string | undefined> {
  const okOnly = options.modal && labels.length === 1 && labels[0] === 'OK';
  const items: vscode.MessageItem[] = okOnly ? [] : labels.map(title => ({ title, isCloseAffordance: CLOSE_LABELS.has(title) }));
  let choice: vscode.MessageItem | undefined;
  if (severity === 'error') {
    choice = await vscode.window.showErrorMessage(text, options, ...items);
  } else if (severity === 'warning') {
    choice = await vscode.window.showWarningMessage(text, options, ...items);
  } else {
    choice = await vscode.window.showInformationMessage(text, options, ...items);
  }
  if (okOnly) {
    return 'OK';
  }
  return choice?.title;
}

/** Above this size a long message is opened in a read-only document instead of a modal dialog. */
const MODAL_LIMIT = 1500;

/**
 * Port of the IMessageConsumer dialogs: the core calls these through client/* requests.
 */
export function registerDialogHandlers(client: RequalityClient): vscode.Disposable[] {
  const subs: vscode.Disposable[] = [];
  subs.push(client.onRequest('client/showMessage', async (p: ShowMessageParams) => {
    const items = (p.items ?? []).filter(i => i);
    if (!p.modal) {
      const text = messageText(p.title, p.message) + (p.detail ? ` (${p.detail})` : '');
      return (await show(p.severity, text, { modal: false }, items)) ?? null;
    }
    const detail = [p.title ? p.message : null, p.detail].filter(x => x).join('\n\n') || undefined;
    return (await show(p.severity, p.title || p.message || 'Requality', { modal: true, detail }, items)) ?? null;
  }));

  subs.push(client.onRequest('client/showLongMessage', async (p: LongMessageParams) => {
    const buttons = (p.buttons ?? []).filter(b => b);
    const message = p.message ?? '';
    if (message.length > MODAL_LIMIT) {
      const doc = await vscode.workspace.openTextDocument({ content: `${p.title ?? ''}\n\n${message}`, language: 'plaintext' });
      await vscode.window.showTextDocument(doc, { preview: true });
    }
    const detail = message.length > MODAL_LIMIT ? 'The full text is opened in an editor.' : message;
    return (await show('info', p.title || 'Requality', { modal: true, detail }, buttons)) ?? null;
  }));

  subs.push(client.onRequest('client/inputBox', async (p: InputBoxParams) => {
    const value = await vscode.window.showInputBox({
      title: p.title ?? undefined,
      prompt: p.prompt ?? undefined,
      value: p.value ?? '',
      ignoreFocusOut: true,
      validateInput: p.validate
        ? async text => (await client.request<string | null>('dialog/validateInput', { session: p.session, text })) ?? undefined
        : undefined,
    });
    return value ?? null;
  }));

  subs.push(client.onRequest('client/selectNode', (p: SelectNodeParams) => selectNode(client, p)));

  subs.push(client.onRequest('client/openView', async (p: { name: string; project: string | null; args: string[] | null }) => {
    const handler = viewHandlers.get(p.name);
    if (!handler) {
      client.log.appendLine(`[client] no handler for view ${p.name}`);
      void vscode.window.showWarningMessage(`Requality: "${p.name}" is not available.`);
      return null;
    }
    return (await handler(p.project, p.args ?? [])) ?? null;
  }));

  subs.push(client.onRequest('client/fileDialog', async (p: { mode: 'open' | 'save'; multi: boolean; title: string; extensions: string[]; names: string[]; path: string; fileName: string }) => {
    const filters: Record<string, string[]> = {};
    (p.extensions ?? []).forEach((ext, i) => {
      const exts = ext.split(';').map(e => e.trim().replace(/^\*\.?/, '')).filter(e => e && e !== '*');
      const name = (p.names ?? [])[i] || ext;
      filters[name] = exts.length ? exts : ['*'];
    });
    const base = p.path ? vscode.Uri.file(p.fileName ? `${p.path}/${p.fileName}` : p.path) : undefined;
    if (p.mode === 'save') {
      const uri = await vscode.window.showSaveDialog({ title: p.title || undefined, defaultUri: base, filters: Object.keys(filters).length ? filters : undefined });
      return uri ? [uri.fsPath] : null;
    }
    const uris = await vscode.window.showOpenDialog({
      title: p.title || undefined, defaultUri: base, canSelectMany: p.multi, canSelectFiles: true, canSelectFolders: false,
      filters: Object.keys(filters).length ? filters : undefined,
    });
    return uris && uris.length ? uris.map(u => u.fsPath) : null;
  }));

  subs.push(client.onRequest('client/directoryDialog', async (p: { title: string; path: string }) => {
    const uris = await vscode.window.showOpenDialog({
      title: p.title || undefined, defaultUri: p.path ? vscode.Uri.file(p.path) : undefined,
      canSelectMany: false, canSelectFiles: false, canSelectFolders: true,
    });
    return uris && uris.length ? uris[0].fsPath : null;
  }));

  subs.push(client.onNotification('settings/update', (p: { key: string; value: unknown }) => {
    void vscode.workspace.getConfiguration('requality').update(p.key, p.value, vscode.ConfigurationTarget.Global);
  }));
  return subs;
}

interface PickNode {
  id: string;
  label: string;
  qid: string;
  type: string;
}

/** Node selection dialog (SelectNodeDialog/SelectDocDialog) as a quick pick over the project's nodes. */
export async function selectNode(client: RequalityClient, p: SelectNodeParams): Promise<string | null> {
  const nodes = await client.request<PickNode[]>('nodes/list', { project: p.project, types: p.types ?? null });
  const items = nodes.filter(n => n.id !== p.exclude).map(n => ({
    label: n.label,
    description: n.type,
    detail: n.qid,
    id: n.id,
  }));
  const pick = await vscode.window.showQuickPick(items, {
    title: p.title ?? 'Select Node',
    matchOnDescription: true,
    matchOnDetail: true,
    ignoreFocusOut: true,
  });
  return pick?.id ?? null;
}
