import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import type { RequalityApi } from '../../src/extension';

/**
 * The scenes of the README pictures, run in VS Code by test/media/run.ts: the ETSI TS 103 096 catalog in the Eclipse
 * view of the Requality Explorer, the properties of a requirement, the popup menu of the plug-in, the Document Editor
 * and the UniEditor. Each scene asks run.ts to capture the window.
 */

type Op = { id: string; type?: string; parent?: string | null; props?: Record<string, unknown> };

let api: RequalityApi;
let shell = '';

const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

async function waitFor<T>(what: string, probe: () => T | undefined | false | Promise<T | undefined | false>, timeoutMs = 120000): Promise<T> {
  const end = Date.now() + timeoutMs;
  for (;;) {
    const v = await probe();
    if (v !== undefined && v !== false && v !== null) {
      return v as T;
    }
    if (Date.now() > end) {
      throw new Error(`Timed out waiting for ${what}`);
    }
    await sleep(200);
  }
}

async function media(pathAndQuery: string): Promise<void> {
  const response = await fetch(process.env.REQUALITY_MEDIA_URL + pathAndQuery);
  if (!response.ok) {
    throw new Error(`${pathAndQuery}: ${await response.text()}`);
  }
}

/** Lets the views paint, then captures the window (without notifications). */
async function capture(name: string, settleMs = 1200): Promise<void> {
  await vscode.commands.executeCommand('notifications.clearAll');
  await sleep(settleMs);
  await media(`/capture?name=${encodeURIComponent(name)}`);
}

async function ops(): Promise<Op[]> {
  return (await api.app.client.request<{ ops: Op[] }>('ui/snapshot', { shell })).ops;
}

function text(o: Op): string {
  const texts = o.props?.texts as string[] | undefined;
  return String(texts?.[0] ?? o.props?.text ?? '');
}

async function tree(): Promise<string> {
  return waitFor('the tree of the explorer', async () => (await ops()).find(o => o.type === 'tree')?.id);
}

async function item(part: string, parent?: string): Promise<Op> {
  return waitFor(`the item "${part}"`, async () =>
    (await ops()).find(o => o.type === 'treeitem' && text(o).includes(part) && (!parent || o.parent === parent)));
}

async function children(parent: string): Promise<Op[]> {
  return waitFor('the children of an item', async () => {
    const list = (await ops()).filter(o => o.type === 'treeitem' && o.parent === parent);
    return list.length ? list : undefined;
  });
}

async function treeEvent(type: string, data: Record<string, unknown>): Promise<void> {
  api.app.client.notify('ui/event', { id: await tree(), type, data });
}

/** The editor tabs of webviews (the web editors and the dialogs of the server). */
function webviewTabs(): number {
  return vscode.window.tabGroups.all.flatMap(g => g.tabs).filter(t => t.input instanceof vscode.TabInputWebview).length;
}

/** The questions of the catalog's format conversion: the user picks the default answer. */
function answerProjectLoading(): void {
  const client = api.app.client as unknown as {
    requestHandlers: Map<string, (p: unknown) => unknown>;
    conn?: { onRequest(m: string, h: (p: unknown) => unknown): void };
  };
  const answer = (method: string, pick: (p: Record<string, unknown>) => unknown) => {
    const h = (p: unknown) => pick(p as Record<string, unknown>);
    client.requestHandlers.set(method, h);
    client.conn?.onRequest(method, h);
  };
  answer('client/showMessage', p => (p.items as string[] | undefined)?.find(i => i) ?? null);
  answer('client/showLongMessage', p => (p.buttons as string[] | undefined)?.find(b => b) ?? null);
  api.app.client.onNotification('ui/openShell', (p: { shell: string; title?: string }) => {
    if (p.title !== 'Project Loading') {
      return;
    }
    void (async () => {
      const snap = await api.app.client.request<{ ops: Op[] }>('ui/snapshot', { shell: p.shell });
      const def = snap.ops.map(o => o.props?.defaultButton).find(b => typeof b === 'string') as string | undefined;
      if (def) {
        api.app.client.notify('ui/event', { id: def, type: 'selection', data: {} });
      }
    })();
  });
}

export async function run(): Promise<void> {
  const ext = vscode.extensions.getExtension('formalized-si.requality-vscode');
  if (!ext) {
    throw new Error('extension not found');
  }
  api = (await ext.activate()) as RequalityApi;
  answerProjectLoading();
  await waitFor('the server', () => api.app.client.state === 'ready');
  await media('/window');
  await vscode.commands.executeCommand('workbench.action.closePanel');
  await vscode.commands.executeCommand('workbench.action.closeAuxiliaryBar');
  fs.cpSync(process.env.REQUALITY_IT_CATALOG!, path.join(process.env.REQUALITY_IT_WORKSPACE!, 'TS103096'), { recursive: true });
  await waitFor('the project loaded', async () => {
    const r = await api.app.client.request<{ projects: Array<{ state: string }> }>('workspace/projects');
    return r.projects.length > 0 && r.projects.every(p => p.state === 'ready');
  }, 300000);
  await vscode.commands.executeCommand('requality.explorerView.focus');
  shell = await waitFor('the Eclipse view', () => api.app.ui.shellOfView('requality.explorerView'));
  const project = await item('TS103096');
  await capture('01-explorer', 2500);

  await treeEvent('expand', { item: project.id });
  const requirements = await item('Requirements', project.id);
  await capture('02-project');

  await treeEvent('expand', { item: requirements.id });
  const [first] = await children(requirements.id);
  await treeEvent('expand', { item: first.id });
  const requirement = (await children(first.id))[0];
  await capture('03-requirements');

  // the properties of a requirement in the panel
  await treeEvent('selection', { selection: [requirement.id], item: requirement.id });
  // the Properties view alone in the panel (the other views of the panel hidden), for its sections to be readable
  for (const view of ['problems', 'todo', 'bookmarks', 'tasks', 'history', 'synchronize', 'progress']) {
    await vscode.commands.executeCommand(`requality.${view}.removeView`).then(undefined, () => undefined);
  }
  await vscode.commands.executeCommand('workbench.action.closeAuxiliaryBar');
  await vscode.commands.executeCommand('requality.properties.focus');
  await waitFor('the Properties view', () => (api.app.ui as unknown as { viewShells: Map<string, string> }).viewShells.get('requality.properties'));
  await capture('04-properties', 3000);

  // the popup menu of the plug-in on the requirement
  await vscode.commands.executeCommand('requality.explorerView.focus');
  await treeEvent('menudetect', { x: 48, y: 150 });
  await waitFor('the popup menu', async () => (await ops()).some(o => o.type === 'menuitem' && /Copy Full Name/.test(String(o.props?.text ?? ''))));
  await capture('05-context-menu', 1500);
  await api.app.ui.postKey('requality.explorerView', { type: 'keydown', key: 'Escape', code: 'Escape', keyCode: 27 });

  // a document of the catalog in the Document Editor (a double click)
  await vscode.commands.executeCommand('workbench.action.closePanel');
  const documents = await item('Documents', project.id);
  await treeEvent('expand', { item: documents.id });
  const [doc] = await children(documents.id);
  let tabs = webviewTabs();
  await treeEvent('selection', { selection: [doc.id], item: doc.id });
  await treeEvent('default', { selection: [doc.id], item: doc.id });
  await waitFor('the Document Editor', () => webviewTabs() > tabs);
  await capture('06-document-editor', 6000);

  // the requirement in the UniEditor
  await vscode.commands.executeCommand('requality.explorerView.focus');
  await treeEvent('selection', { selection: [requirement.id], item: requirement.id });
  tabs = webviewTabs();
  await vscode.commands.executeCommand('requality.openUniEditor');
  await waitFor('the UniEditor', () => webviewTabs() > tabs);
  await capture('07-unieditor', 6000);
}
