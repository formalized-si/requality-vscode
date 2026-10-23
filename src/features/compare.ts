import * as vscode from 'vscode';
import { App } from '../app';

/** The scheme of the read-only documents of compare sides that are not files (local history, revisions, clipboard). */
export const COMPARE_SCHEME = 'requality-compare';

/** A side of a diff opened by the server: a workspace file (path) or contents kept by the server (id). */
interface Side {
  path?: string;
  id?: string;
  name?: string;
  label?: string;
}

function sideUri(side: Side): vscode.Uri {
  if (side.path) {
    return vscode.Uri.file(side.path);
  }
  return vscode.Uri.from({ scheme: COMPARE_SCHEME, path: `/${side.id ?? 'none'}/${side.name || 'content'}` });
}

/**
 * The Eclipse compare editor of text comparisons: the server's CompareEditor opens the VS Code diff editor
 * (client/openDiff) and read-only documents (client/openContent) whose contents it serves (compare/content).
 */
/** The diffs of the server the extension reported (exported for the tests): the last active one and the closed ones. */
export const diffState = { active: undefined as unknown, closed: [] as unknown[] };

export function activateCompare(app: App): void {
  const { context, client } = app;
  const subs = context.subscriptions;
  subs.push(vscode.workspace.registerTextDocumentContentProvider(COMPARE_SCHEME, {
    provideTextDocumentContent: async (uri: vscode.Uri) => {
      const id = uri.path.split('/')[1];
      const text = await client.request<string | null>('compare/content', { id });
      return text ?? '';
    },
  }));
  // The diffs the server opened, by their two URIs, with the sides as the server sent them: the Link with Editor of the
  // Synchronize view selects the element of the active diff (editor/activeDiff) and forgets a closed one
  // (editor/diffClosed).
  const opened = new Map<string, { left: Side; right: Side }>();
  const diffKey = (left: vscode.Uri, right: vscode.Uri) => `${left.toString()}\n${right.toString()}`;
  subs.push(client.onNotification('client/openDiff', (p: { title?: string; left: Side; right: Side }) => {
    const left = sideUri(p.left);
    const right = sideUri(p.right);
    opened.set(diffKey(left, right), { left: p.left, right: p.right });
    void vscode.commands.executeCommand('vscode.diff', left, right, p.title ?? 'Compare', { preview: false });
  }));
  const diffOf = (tab: vscode.Tab | undefined) =>
    tab && tab.input instanceof vscode.TabInputTextDiff ? opened.get(diffKey(tab.input.original, tab.input.modified)) : undefined;
  let lastActive: string | undefined;
  const notifyActiveDiff = () => {
    const tab = vscode.window.tabGroups.activeTabGroup.activeTab;
    const sides = diffOf(tab);
    const key = sides && tab && tab.input instanceof vscode.TabInputTextDiff ? diffKey(tab.input.original, tab.input.modified) : undefined;
    if (key !== lastActive) {
      lastActive = key;
      if (sides && client.state === 'ready') {
        diffState.active = sides;
        client.notify('editor/activeDiff', sides);
      }
    }
  };
  subs.push(vscode.window.tabGroups.onDidChangeTabs(e => {
    for (const tab of e.closed) {
      const sides = diffOf(tab);
      if (sides && tab.input instanceof vscode.TabInputTextDiff) {
        opened.delete(diffKey(tab.input.original, tab.input.modified));
        if (client.state === 'ready') {
          diffState.closed.push(sides);
          client.notify('editor/diffClosed', sides);
        }
      }
    }
    notifyActiveDiff();
  }));
  subs.push(vscode.window.tabGroups.onDidChangeTabGroups(() => notifyActiveDiff()));
  // The active editor of the workbench (GenericHistoryView "Link with Editor"): the file of the active text editor.
  const notifyActiveEditor = (editor: vscode.TextEditor | undefined) => {
    if (client.state === 'ready') {
      client.notify('editor/activeTextEditor', { path: editor && editor.document.uri.scheme === 'file' ? editor.document.uri.fsPath : null });
    }
  };
  subs.push(vscode.window.onDidChangeActiveTextEditor(notifyActiveEditor));
  subs.push(client.onDidChangeState(s => {
    if (s === 'ready') {
      notifyActiveEditor(vscode.window.activeTextEditor);
    }
  }));
  // An Eclipse editor saves with KEEP_HISTORY: the contents a VS Code save replaces become a local history state.
  subs.push(vscode.workspace.onWillSaveTextDocument(e => {
    if (e.document.uri.scheme === 'file' && client.state === 'ready') {
      e.waitUntil(client.request('resources/willSave', { path: e.document.uri.fsPath }).then(() => [], () => []));
    }
  }));
  // The active editor of Eclipse for Replace With > Clipboard: the active text editor, its file and selection offsets.
  subs.push(client.onRequest('client/activeTextEditor', () => {
    const editor = vscode.window.activeTextEditor;
    if (!editor || editor.document.uri.scheme !== 'file') {
      return null;
    }
    return {
      path: editor.document.uri.fsPath,
      selectionStart: editor.document.offsetAt(editor.selection.start),
      selectionEnd: editor.document.offsetAt(editor.selection.end),
    };
  }));
  // The dirty editors of Eclipse (file buffers with unsaved changes): the files of the VS Code documents with unsaved
  // changes (the Delete Resources refactoring warns about them).
  subs.push(client.onRequest('client/dirtyDocuments', () =>
    vscode.workspace.textDocuments.filter(d => d.isDirty && d.uri.scheme === 'file').map(d => d.uri.fsPath)));
  // IDocument.replace of the editor's document: the document changes (and becomes dirty) as in Eclipse.
  subs.push(client.onRequest('client/replaceText', async (p: { path: string; start: number; end: number; text: string }) => {
    const doc = await vscode.workspace.openTextDocument(vscode.Uri.file(p.path));
    const edit = new vscode.WorkspaceEdit();
    edit.replace(doc.uri, new vscode.Range(doc.positionAt(p.start), doc.positionAt(p.end)), p.text);
    return vscode.workspace.applyEdit(edit);
  }));
  // A read-only revision (FileRevisionEditorInput): the text editor, or the editor of VS Code chosen by Open With.
  subs.push(client.onNotification('client/openContent', (p: Side & { title?: string; editor?: string }) => {
    if (p.editor) {
      void Promise.resolve(vscode.commands.executeCommand('vscode.openWith', sideUri(p), p.editor)).catch(e =>
        void vscode.window.showErrorMessage(`A problem occurred while opening the editor: ${e instanceof Error ? e.message : e}`));
      return;
    }
    void vscode.workspace.openTextDocument(sideUri(p)).then(doc => vscode.window.showTextDocument(doc, { preview: true }));
  }));
}
