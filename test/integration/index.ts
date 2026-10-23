import * as assert from 'assert';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import type { RequalityApi } from '../../src/extension';
import type { ExplorerItem } from '../../src/views/explorer';

type Test = { name: string; body: () => Promise<void> };
const tests: Test[] = [];
const test = (name: string, body: () => Promise<void>) => tests.push({ name, body });

async function waitFor<T>(what: string, probe: () => T | undefined | Promise<T | undefined>, timeoutMs = 60000): Promise<T> {
  const end = Date.now() + timeoutMs;
  for (;;) {
    const v = await probe();
    if (v !== undefined && v !== null && v !== false) {
      return v as T;
    }
    if (Date.now() > end) {
      throw new Error(`Timed out waiting for ${what}`);
    }
    await new Promise(r => setTimeout(r, 200));
  }
}

/** Webview panel tabs whose label matches. */
function panelTabs(match: (label: string) => boolean): vscode.Tab[] {
  return vscode.window.tabGroups.all.flatMap(g => g.tabs).filter(t => t.input instanceof vscode.TabInputWebview && match(t.label));
}

async function closeTabs(tabs: vscode.Tab[]): Promise<void> {
  if (tabs.length) {
    await vscode.window.tabGroups.close(tabs);
  }
}

let api: RequalityApi;
let extensionPath = '';

/**
 * The exports of a module of the extension: the instance the extension uses (the extension may run from another
 * folder). On Windows the module cache keeps the path as the extension host gave it (the drive letter can be in another
 * case), and a require with another spelling of the same path loads a second instance of the module.
 */
function extensionModule(...parts: string[]): Record<string, unknown> {
  const file = path.join(extensionPath, 'out', 'src', ...parts) + '.js';
  const same = (key: string) => (process.platform === 'win32' ? key.toLowerCase() === file.toLowerCase() : key === file);
  const key = Object.keys(require.cache).find(same);
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return key ? require.cache[key]!.exports : require(file);
}

/** The help state of the extension's own module instance. */
function helpState(): { title: string } {
  return extensionModule('features', 'workbench').helpState as { title: string };
}

/** The window title of the extension's own module instance (client/windowTitle). */
function windowState(): { title: string; template: string } {
  return extensionModule('features', 'workbench').windowState as { title: string; template: string };
}

/** The same file: on Windows the paths can differ in case and in short (8.3) names of folders. */
function samePath(a: string, b: string): boolean {
  const real = (p: string) => {
    try {
      p = fs.realpathSync.native(p);
    } catch {
      // the file is not there: compare the paths as they are
    }
    return process.platform === 'win32' ? p.toLowerCase() : p;
  };
  return real(a) === real(b);
}

/** The last lines of the extension log. */
const logLines: string[] = [];
let partial = '';
function remember(text: string): void {
  if (process.env.REQUALITY_IT_LOG) {
    try {
      fs.appendFileSync(process.env.REQUALITY_IT_LOG, text);
    } catch {
      // the log file is a convenience
    }
  }
  const parts = (partial + text).split('\n');
  partial = parts.pop() ?? '';
  logLines.push(...parts);
  if (logLines.length > 400) {
    logLines.splice(0, logLines.length - 400);
  }
}
let project: ExplorerItem;

async function children(item?: ExplorerItem): Promise<ExplorerItem[]> {
  return api.app.explorer.getChildren(item);
}

/** Breadth-first search of the explorer for an item whose context value has the tag (waits for the project load). */
async function findItem(tag: string): Promise<ExplorerItem> {
  try {
    await waitFor('project content', async () => ((await children(project)).length ? true : undefined), 180000);
  } catch (e) {
    console.log('workspace/projects:', JSON.stringify(await api.app.client.request('workspace/projects')));
    console.log('project item:', JSON.stringify(project));
    throw e;
  }
  const queue: ExplorerItem[] = [project];
  let visited = 0;
  while (queue.length && visited < 400) {
    const it = queue.shift()!;
    visited++;
    for (const c of await children(it)) {
      if ((' ' + (c.contextValue ?? '') + ' ').includes(' ' + tag + ' ')) {
        return c;
      }
      if (c.hasChildren) {
        queue.push(c);
      }
    }
  }
  throw new Error(`no explorer item tagged ${tag}`);
}

/** Up to {@code count} explorer items tagged with {@code tag}, breadth first. */
async function findItems(tag: string, count: number): Promise<ExplorerItem[]> {
  await findItem(tag);
  const out: ExplorerItem[] = [];
  const queue: ExplorerItem[] = [project];
  let visited = 0;
  while (queue.length && visited < 2000 && out.length < count) {
    const it = queue.shift()!;
    visited++;
    for (const c of await children(it)) {
      if ((' ' + (c.contextValue ?? '') + ' ').includes(' ' + tag + ' ') && out.length < count) {
        out.push(c);
      }
      if (c.hasChildren) {
        queue.push(c);
      }
    }
  }
  return out;
}

/** Questions the server asked the user (answered by the test like a user choosing the default button). */
const asked: string[] = [];

/** The test plays the user of modal questions: it picks the first (default) button. */
function answerDialogs(): void {
  const client = api.app.client as unknown as {
    requestHandlers: Map<string, (p: unknown) => unknown>;
    conn?: { onRequest(m: string, h: (p: unknown) => unknown): void };
  };
  const answer = (method: string, pick: (p: Record<string, unknown>) => unknown) => {
    const h = (p: unknown) => {
      const params = p as Record<string, unknown>;
      asked.push(`${method}: ${params.title ?? ''} ${params.message ?? ''}`.slice(0, 200));
      return pick(params);
    };
    client.requestHandlers.set(method, h);
    client.conn?.onRequest(method, h);
  };
  answer('client/showMessage', p => (p.items as string[] | undefined)?.find(i => i) ?? null);
  answer('client/showLongMessage', p => (p.buttons as string[] | undefined)?.find(b => b) ?? null);
  // Questions shown as workbench dialogs (BigMessageDialog): the user presses the default button.
  api.app.client.onNotification('ui/openShell', (p: { shell: string; title?: string }) => {
    if (p.title !== 'Project Loading') {
      return;
    }
    asked.push(`dialog: ${p.title}`);
    void (async () => {
      const snap = await api.app.client.request<{ ops: Array<{ id: string; type?: string; props?: Record<string, unknown> }> }>('ui/snapshot', { shell: p.shell });
      const def = snap.ops.map(o => o.props?.defaultButton).find(b => typeof b === 'string') as string | undefined;
      const button = def ?? snap.ops.find(o => o.type === 'button')?.id;
      if (button) {
        api.app.client.notify('ui/event', { id: button, type: 'selection', data: {} });
      }
    })();
  });
}

test('the extension activates and its server starts', async () => {
  const ext = vscode.extensions.getExtension('formalized-si.requality-vscode');
  assert.ok(ext, 'extension not found');
  api = (await ext.activate()) as RequalityApi;
  extensionPath = ext.extensionPath;
  // The extension log (client lines and server output) is kept for the report of a failing test.
  const log = api.app.log as unknown as { appendLine(s: string): void; append(s: string): void };
  const appendLine = log.appendLine.bind(log);
  const append = log.append.bind(log);
  log.appendLine = (line: string) => {
    appendLine(line);
    remember(line + '\n');
  };
  log.append = (text: string) => {
    append(text);
    remember(text);
  };
  answerDialogs();
  await waitFor('server ready', () => api.app.client.state === 'ready', 180000);
});

test('a catalog copied into the workspace is discovered and loads', async () => {
  fs.cpSync(process.env.REQUALITY_IT_CATALOG!, path.join(process.env.REQUALITY_IT_WORKSPACE!, 'TS103096'), { recursive: true });
  const ps = await waitFor('project ready', async () => {
    const r = await api.app.client.request<{ projects: Array<{ state: string }> }>('workspace/projects');
    return r.projects.length && r.projects.every(p => p.state === 'ready' || p.state === 'broken') ? r.projects : undefined;
  }, 240000).catch(async e => {
    console.log('workspace/projects:', JSON.stringify(await api.app.client.request('workspace/projects')), 'asked:', asked);
    throw e;
  });
  assert.equal(ps[0].state, 'ready', JSON.stringify(ps));
  console.log('    questions answered:', JSON.stringify(asked));
});

test('the explorer shows the catalog project', async () => {
  const roots = await waitFor('explorer roots', async () => {
    const r = await children();
    return r.length ? r : undefined;
  }, 120000);
  project = roots.find(r => r.label === 'TS103096' || String(r.label).includes('TS103096'))!;
  assert.ok(project, JSON.stringify(roots.map(r => r.label)));
  await vscode.commands.executeCommand('requality.explorer.focus');
});

test('opening a document shows the Document Editor (ReqMarker)', async () => {
  const doc = await findItem('is.Document');
  await vscode.commands.executeCommand('requality.open', doc);
  const tabs = await waitFor('document editor tab', () => {
    const t = panelTabs(l => l.includes(String(doc.label).replace(/\.x?html?$/, '').slice(0, 8)));
    return t.length ? t : undefined;
  });
  await closeTabs(tabs);
});

test('Edit in UniEditor opens the UniEditor part', async () => {
  const req = await findItem('is.Requirement');
  await vscode.commands.executeCommand('requality.openUniEditor', req);
  const tabs = await waitFor('UniEditor tab', () => {
    const t = panelTabs(l => l.startsWith('UniEditor'));
    return t.length ? t : undefined;
  });
  await closeTabs(tabs);
});

test('ten web editors work at once (no Internet Explorer connection limit)', async () => {
  // On Windows Eclipse warned that the Internet Explorer engine of the SWT Browser allows few connections per
  // server, so more than 8 web editors needed a registry fix (Activator: MAXCONNECTIONSPERSERVER). The editors are
  // VS Code webviews (Chromium) here: ten UniEditors open at once and every page connects to the server.
  // The deepest requirements (breadth first: the last ones found): the UniEditor of a requirement shows its whole
  // subtree, which takes minutes to render on a slow machine for the top-level ones
  const all = await findItems('is.Requirement', 400);
  const distinct = all.filter((r, i) => all.findIndex(o => o.id === r.id) === i);
  const reqs = distinct.slice(-10);
  assert.strictEqual(reqs.length, 10, 'ten requirements in the catalog');
  // each editor is opened and shown until its page has connected (VS Code creates the page of a webview when its tab
  // is shown, then keeps it while hidden), as a user opening them one after the other; all stay open
  for (const r of reqs) {
    const before = (await api.app.client.request<Array<{ ready: boolean }>>('ui/webEditors')).filter(e => e.ready).length;
    await vscode.commands.executeCommand('requality.openUniEditor', r);
    await waitFor(`the page of ${r.label}`, async () => {
      const list = await api.app.client.request<Array<{ ready: boolean }>>('ui/webEditors');
      return list.filter(e => e.ready).length > before ? true : undefined;
    }, 30000);
  }
  try {
    await waitFor('ten connected web editors', async () => {
      const list = await api.app.client.request<Array<{ title: string; ready: boolean }>>('ui/webEditors');
      return list.filter(e => e.ready).length >= 10 ? list : undefined;
    }, 30000); // within the 150 s of a test, so that the state of the editors is reported
  } catch (e) {
    console.log('web editors:', JSON.stringify(await api.app.client.request('ui/webEditors')));
    throw e;
  }
  await closeTabs(panelTabs(l => l.startsWith('UniEditor')));
});

test('Copy Full Name puts the name of the node on the clipboard', async () => {
  const req = await findItem('is.Requirement');
  await vscode.env.clipboard.writeText('');
  await vscode.commands.executeCommand('requality.copyQId', req);
  const text = await waitFor('clipboard', async () => (await vscode.env.clipboard.readText()) || undefined);
  assert.ok(text.length > 0);
});

test('the Properties view shows the property page', async () => {
  await vscode.commands.executeCommand('requality.properties.focus');
  const req = await findItem('is.Requirement');
  await vscode.commands.executeCommand('requality.showProperties', req);
  // The server hosts the PropertySheet part in the requality.properties webview view.
  await waitFor('properties shell', () => (api.app.ui as unknown as { viewShells: Map<string, string> }).viewShells.get('requality.properties'));
});

test('the New Requality Project wizard opens and cancels', async () => {
  const before = panelTabs(() => true).length;
  const run = vscode.commands.executeCommand('requality.newProject');
  const tabs = await waitFor('wizard tab', () => {
    const t = panelTabs(() => true);
    return t.length > before ? t.slice(before) : undefined;
  });
  await closeTabs(tabs);
  await run;
});

test('Requality Compare opens the TreeNode compare viewer for two node files', async () => {
  const ws = process.env.REQUALITY_IT_WORKSPACE!;
  const reqDir = path.join(ws, 'TS103096', 'root', 'Requirements');
  const files = fs.readdirSync(reqDir).filter(f => f.endsWith('.json')).slice(0, 1).map(f => path.join(reqDir, f));
  files.push(path.join(ws, 'TS103096', 'root', 'Requirements.json'));
  await vscode.commands.executeCommand('requality.compare', vscode.Uri.file(files[0]), files.map(f => vscode.Uri.file(f)));
  const tabs = await waitFor('compare tab', () => {
    const t = panelTabs(l => l.startsWith('Compare'));
    return t.length ? t : undefined;
  });
  await closeTabs(tabs);
});

test('the Requality manual opens and shows its first page', async () => {
  helpState().title = '';
  await vscode.commands.executeCommand('requality.help');
  const tabs = await waitFor('manual tab', () => {
    const t = panelTabs(l => l === 'Requality Manual');
    return t.length ? t : undefined;
  });
  // The page is fetched through the webview resource loader and shown with its own title.
  const title = await waitFor('manual page loaded', () => helpState().title || undefined);
  assert.ok(title.startsWith('Requality'), title);
  await closeTabs(tabs);
});

type Op = { id: string; type?: string; props?: Record<string, unknown> };

/** Waits for a server dialog (an editor panel) with the title and returns its shell id. */
async function awaitDialog(title: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const timer = setTimeout(() => {
      sub.dispose();
      reject(new Error(`no dialog "${title}"`));
    }, 60000);
    const sub = api.app.client.onNotification('ui/openShell', (p: { shell: string; title?: string; host?: string }) => {
      if (p.title === title && p.host !== 'view') {
        clearTimeout(timer);
        sub.dispose();
        resolve(p.shell);
      }
    });
  });
}

/** Types into the first text field of a dialog and presses its default button, as the user would. */
async function typeAndConfirm(shell: string, text: string): Promise<void> {
  const snap = await api.app.client.request<{ ops: Op[] }>('ui/snapshot', { shell });
  const field = snap.ops.find(o => o.type === 'text');
  assert.ok(field, 'no text field');
  api.app.client.notify('ui/event', { id: field.id, type: 'modify', data: { text, selStart: text.length, selEnd: text.length } });
  const def = snap.ops.map(o => o.props?.defaultButton).find(b => typeof b === 'string') as string | undefined;
  assert.ok(def, 'no default button');
  api.app.client.notify('ui/event', { id: def, type: 'selection', data: {} });
}

type ProjectInfo = { name: string; state: string; path: string; localName: string };

async function projects(): Promise<ProjectInfo[]> {
  return (await api.app.client.request<{ projects: ProjectInfo[] }>('workspace/projects')).projects;
}

/** A widget of a dialog by type and (mnemonic-free) text. */
async function widget(shell: string, type: string, text?: string): Promise<string> {
  const snap = await api.app.client.request<{ ops: Op[] }>('ui/snapshot', { shell });
  const op = snap.ops.find(o => o.type === type && (text === undefined || String(o.props?.text ?? '').replace(/&/g, '') === text));
  assert.ok(op, `no ${type} "${text}" in ${JSON.stringify(snap.ops.map(o => [o.type, o.props?.text]))}`);
  return op.id;
}

async function pressDefault(shell: string): Promise<void> {
  const snap = await api.app.client.request<{ ops: Op[] }>('ui/snapshot', { shell });
  const def = snap.ops.map(o => o.props?.defaultButton).find(b => typeof b === 'string') as string | undefined;
  assert.ok(def, 'no default button');
  api.app.client.notify('ui/event', { id: def, type: 'selection', data: {} });
}

test('Close Project and Open Project of the explorer menu close and reopen the catalog', async () => {
  await vscode.commands.executeCommand('requality.project.close', project);
  await waitFor('closed project', async () => ((await projects()).find(p => p.localName === 'TS103096')?.state === 'closed' ? true : undefined));
  const closed = (await children()).find(r => r.id === project.id)!;
  assert.ok(closed.contextValue.includes('closed'), closed.contextValue);
  assert.equal(closed.hasChildren, false);
  await vscode.commands.executeCommand('requality.project.open', closed);
  await waitFor('reopened project', async () => ((await projects()).find(p => p.localName === 'TS103096')?.state === 'ready' ? true : undefined), 120000)
    .catch(async e => {
      console.log('    workspace/projects:', JSON.stringify(await projects()));
      throw e;
    });
  await waitFor('project content', async () => ((await children(project)).length ? true : undefined), 120000);
});

test('Copy, Paste and Delete of a project copy the catalog and delete the copy', async () => {
  await vscode.commands.executeCommand('requality.project.copy', project);
  const copyDialog = awaitDialog('Copy Project');
  const paste = vscode.commands.executeCommand('requality.project.paste', project);
  const shell = await copyDialog;
  // The catalog is not at its default location: the copy goes to the default location of its new name.
  api.app.client.notify('ui/event', { id: await widget(shell, 'button', 'Use default location'), type: 'selection', data: { selection: true } });
  await new Promise(r => setTimeout(r, 300));
  await pressDefault(shell);
  await paste;
  const name = 'Copy of TS103096-STF517';
  const copy = await waitFor('copied project', async () => (await projects()).find(p => p.name === name && p.state === 'ready'), 120000);
  assert.ok(fs.existsSync(path.join(process.env.REQUALITY_IT_WORKSPACE!, name, '.project')));
  const item = await waitFor('copy in the explorer', async () => (await children()).find(r => r.id === 'project:' + copy.localName));
  const deleteDialog = awaitDialog('Delete Resources');
  const del = vscode.commands.executeCommand('requality.project.delete', item);
  const ds = await deleteDialog;
  api.app.client.notify('ui/event', { id: await widget(ds, 'button', 'Delete project contents on disk (cannot be undone)'), type: 'selection', data: { selection: true } });
  await new Promise(r => setTimeout(r, 300));
  await pressDefault(ds);
  await del;
  await waitFor('copy deleted', async () => (!(await projects()).some(p => p.name === name) && !fs.existsSync(copy.path) ? true : undefined), 120000);
});

test('another Eclipse project is listed with its files and a file opens in its editor', async () => {
  const dir = path.join(process.env.REQUALITY_IT_WORKSPACE!, 'Other');
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, '.project'), '<?xml version="1.0" encoding="UTF-8"?>\n<projectDescription><name>Other</name><natures></natures></projectDescription>\n');
  fs.writeFileSync(path.join(dir, 'notes.txt'), 'notes');
  fs.writeFileSync(path.join(dir, 'notes2.txt'), 'notes, second version');
  await vscode.commands.executeCommand('requality.refresh');
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  assert.ok(other.contextValue.includes('nonRequality'), other.contextValue);
  const file = (await children(other)).find(c => c.label === 'notes.txt');
  assert.ok(file && file.kind === 'resource', JSON.stringify(await children(other)));
  await vscode.commands.executeCommand('requality.open', file);
  await waitFor('the file editor', () => {
    const doc = vscode.window.activeTextEditor?.document;
    return doc && samePath(doc.uri.fsPath, path.join(dir, 'notes.txt')) ? true : undefined;
  });
  await vscode.commands.executeCommand('workbench.action.closeActiveEditor');
});

test('Go Into the other project shows its files as the roots; Back shows the projects again', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  // GoIntoActionProvider: Go Into is in the popup of an open project
  await vscode.commands.executeCommand('requality.goInto', other);
  await waitFor('the files as roots', async () => ((await children()).some(r => r.label === 'notes.txt') ? true : undefined));
  // the title tool tip of the Eclipse view (the input) is the description of the VS Code view
  assert.strictEqual(api.app.explorer.view.description, 'Other');
  await vscode.commands.executeCommand('requality.explorer.back');
  await waitFor('the projects as roots', async () => ((await children()).some(r => r.label === 'Other') ? true : undefined));
  assert.strictEqual(api.app.explorer.view.description, undefined);
});

test('Properties of a file opens the property dialog with the Resource page', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  const file = (await children(other)).find(c => c.label === 'notes.txt');
  assert.ok(file);
  const dialog = awaitDialog('Properties for notes.txt');
  const run = vscode.commands.executeCommand('requality.properties', file);
  const shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('Resource') && snap.includes('notes.txt'), snap.slice(0, 2000));
  await pressDefault(shell);
  await run;
});

test('Show In > Terminal opens a terminal in the folder of the file', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  const file = (await children(other)).find(c => c.label === 'notes.txt');
  assert.ok(file);
  type Item = { label?: string; index?: number; children?: Item[] };
  const popup = 'com.unitesk.requality.views.RequalityExplorer.popup';
  const items = await api.app.client.request<Item[]>('menu/items', { menu: popup, nodes: [file.id], source: 'explorer' });
  const terminalItem = items.find(i => i.label === 'Show In')?.children?.find(c => c.label === 'Terminal');
  assert.ok(terminalItem?.index !== undefined, JSON.stringify(items));
  const before = vscode.window.terminals.length;
  await api.app.client.request('menu/run', { menu: popup, index: terminalItem.index });
  const terminal = await waitFor('a new terminal', () => (vscode.window.terminals.length > before ? vscode.window.terminals[vscode.window.terminals.length - 1] : undefined));
  terminal.dispose();
});

/** Presses the Cancel button of a dialog. */
async function cancel(shell: string): Promise<void> {
  api.app.client.notify('ui/event', { id: await widget(shell, 'button', 'Cancel'), type: 'selection', data: {} });
}

test('the view menu opens Filters and Customization and Select Working Set', async () => {
  let dialog = awaitDialog('Filters and Customization');
  let run = vscode.commands.executeCommand('requality.filters');
  let shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('Pre-set filters') && snap.includes('User filters') && snap.includes('Content'), snap.slice(0, 2000));
  await cancel(shell);
  await run;
  dialog = awaitDialog('Select Working Set');
  run = vscode.commands.executeCommand('requality.workingSet.select');
  shell = await dialog;
  await cancel(shell);
  await run;
});

test('New > Folder of a project and Ctrl+N open the New wizards', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  let dialog = awaitDialog('New Folder');
  let run = vscode.commands.executeCommand('requality.newResource.folder', other);
  let shell = await dialog;
  await cancel(shell);
  await run;
  await api.app.explorer.view.reveal(other, { select: true, focus: true });
  dialog = awaitDialog('New');
  run = vscode.commands.executeCommand('requality.newWizard');
  shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('General') && snap.includes('Requality'), snap.slice(0, 2000));
  await cancel(shell);
  await run;
});

test('File > Import... and Export... open the wizard selection with the Requality wizards', async () => {
  for (const [cmd, title] of [['requality.fileImport', 'Import'], ['requality.fileExport', 'Export']]) {
    const dialog = awaitDialog(title);
    const run = vscode.commands.executeCommand(cmd);
    const shell = await dialog;
    const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
    assert.ok(snap.includes('General') && snap.includes('Requality'), snap.slice(0, 2000));
    await cancel(shell);
    await run;
  }
});

test('Add Bookmark of a file is listed by the Bookmarks view', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  const file = (await children(other)).find(c => c.label === 'notes.txt');
  assert.ok(file);
  await vscode.commands.executeCommand('requality.addBookmark', file);
  await vscode.commands.executeCommand('requality.bookmarks.focus');
  // the view part lists the bookmark (its table row shows the name of the file)
  await waitFor('the bookmark in the view', async () => {
    const shell = api.app.ui.shellOfView('requality.bookmarks');
    if (!shell) {
      return undefined;
    }
    const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
    return snap.includes('notes.txt') ? true : undefined;
  }, 60000);
});

test('Compare With > Each Other of two files opens the VS Code diff editor', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  const files = (await children(other)).filter(c => c.label === 'notes.txt' || c.label === 'notes2.txt');
  assert.strictEqual(files.length, 2, JSON.stringify(await children(other)));
  await vscode.commands.executeCommand('requality.compareWith.eachOther', files[0], files);
  const diff = await waitFor('the diff editor', () => {
    const input = vscode.window.tabGroups.activeTabGroup.activeTab?.input;
    return input instanceof vscode.TabInputTextDiff ? input : undefined;
  }, 60000);
  const names = [path.basename(diff.original.fsPath), path.basename(diff.modified.fsPath)].sort();
  assert.deepStrictEqual(names, ['notes.txt', 'notes2.txt']);
  // the active diff is reported to the server with its sides (Link with Editor of the Synchronize view), then its close
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const diffState = extensionModule('features', 'compare').diffState as { active?: { left: { path?: string } }; closed: unknown[] };
  await waitFor('the active diff reported', () => diffState.active?.left?.path ? true : undefined);
  const closedBefore = diffState.closed.length;
  await vscode.commands.executeCommand('workbench.action.closeActiveEditor');
  await waitFor('the closed diff reported', () => diffState.closed.length > closedBefore ? true : undefined);
});

test('a save keeps the local history: Show Local History lists it, Previous from Local History restores it', async () => {
  const dir = path.join(process.env.REQUALITY_IT_WORKSPACE!, 'Other');
  const target = path.join(dir, 'notes.txt');
  const doc = await vscode.workspace.openTextDocument(target);
  const editor = await vscode.window.showTextDocument(doc);
  await editor.edit(e => e.insert(new vscode.Position(0, 0), 'edited '));
  await doc.save();
  assert.strictEqual(fs.readFileSync(target, 'utf8'), 'edited notes');
  await vscode.commands.executeCommand('workbench.action.closeActiveEditor');
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  const file = (await children(other)).find(c => c.label === 'notes.txt');
  assert.ok(file);
  // Team > Show Local History: the History view shows the revisions of the file
  await vscode.commands.executeCommand('requality.team.showLocalHistory', file);
  await waitFor('the revisions in the History view', async () => {
    const shell = api.app.ui.shellOfView('requality.history');
    if (!shell) {
      return undefined;
    }
    // the workspace file and the state the save kept (Group Revisions by Date is off by default, as in Eclipse)
    const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
    return (snap.match(/"type":"treeitem"/g) ?? []).length >= 2 ? true : undefined;
  }, 60000);
  // Replace With > Previous from Local History: the contents before the save come back
  await vscode.commands.executeCommand('requality.replaceWith.previous', file);
  await waitFor('the previous contents', () => (fs.readFileSync(target, 'utf8') === 'notes' ? true : undefined), 60000);
});

test('Configure > Configure and Detect Nested Projects... of a project opens the import wizard', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  const dialog = awaitDialog('Import Projects from File System or Archive');
  const run = vscode.commands.executeCommand('requality.configureProject', other);
  const shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('Other'), snap.slice(0, 2000));
  await cancel(shell);
  await run;
});

test('the view menu of the Tasks view opens Configure Columns', async () => {
  await vscode.commands.executeCommand('requality.tasks.focus');
  await waitFor('the Tasks view part', () => api.app.ui.shellOfView('requality.tasks') ? true : undefined, 60000);
  const dialog = awaitDialog('Configure Columns');
  const run = vscode.commands.executeCommand('requality.tasks.columns');
  const shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('Description'), snap.slice(0, 2000));
  await cancel(shell);
  await run;
});

test('the Synchronize view of Team opens in the panel (Alt+Shift+Q Y)', async () => {
  await vscode.commands.executeCommand('requality.synchronize.focus');
  await waitFor('the Synchronize view part', () => api.app.ui.shellOfView('requality.synchronize') ? true : undefined, 60000);
});

test('the Problems view of the IDE opens in the panel (Alt+Shift+Q X)', async () => {
  await vscode.commands.executeCommand('requality.problems.focus');
  await waitFor('the Problems view part', () => api.app.ui.shellOfView('requality.problems') ? true : undefined, 60000);
});

test('Window > Show View > Other... opens the Show View dialog with the view categories', async () => {
  const dialog = awaitDialog('Show View');
  const run = vscode.commands.executeCommand('requality.showView.other');
  const shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  // the categories of the views (collapsed, as the Eclipse dialog opens without saved expansion state)
  for (const label of ['General', 'Requality', 'Version Control (Team)']) {
    assert.ok(snap.includes(label), `${label} in ${snap.slice(0, 1500)}`);
  }
  await cancel(shell);
  await run;
});

test('the Progress view opens in the panel (Window > Show View > Progress)', async () => {
  await vscode.commands.executeCommand('requality.showView.progress');
  await waitFor('the Progress view part', () => api.app.ui.shellOfView('requality.progress') ? true : undefined, 60000);
  const shell = api.app.ui.shellOfView('requality.progress')!;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  // the empty page of the view, or the rows of the operations (with their progress bars)
  assert.ok(snap.includes('No operations to display at this time.') || snap.includes('"type":"progress"'), snap.slice(0, 1500));
});

test('Preferences has the Eclipse platform pages (General > Workspace > Local History, Compare/Patch, Team)', async () => {
  const dialog = awaitDialog('Preferences');
  const run = vscode.commands.executeCommand('requality.preferences');
  const shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  for (const page of ['General', 'Workspace', 'Local History', 'Compare/Patch', 'Team', 'Ignored Resources', 'File Content', 'Models', 'Requality']) {
    assert.ok(snap.includes(page), `${page} in ${snap.slice(0, 1500)}`);
  }
  await cancel(shell);
  await run;
});

test('Window > Perspective: the window title in the status bar, Open Perspective > Other... and Reset Perspective...', async () => {
  // IDEWorkbenchWindowAdvisor's title (the product name with the default preferences), the title of the VS Code window
  // with its active editor in place of Eclipse's
  assert.equal(await waitFor('the window title', () => windowState().title || undefined), 'Requality');
  await waitFor('the window title of VS Code', () => vscode.workspace.getConfiguration('window').get<string>('title') === '${dirty}${activeEditorMedium}${separator}Requality' ? true : undefined);
  // the layout of the Requality perspective was applied when it was opened the first time
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const layout = extensionModule('features', 'workbench').layoutState as { applied: string[] };
  assert.ok(layout.applied.includes('com.unitesk.requality.main.perspective'), layout.applied.join());
  const appliedBefore = layout.applied.length;
  let dialog = awaitDialog('Open Perspective');
  let run = vscode.commands.executeCommand('requality.perspective.openOther');
  let shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('Requality (default)') && snap.includes('Team Synchronizing'), snap.slice(0, 2000));
  await cancel(shell);
  await run;
  const shown: string[] = [];
  const subs = [api.app.client.onNotification('client/showView', (p: { id: string }) => shown.push(p.id))];
  const reset = new Promise<{ perspective: string }>(resolve => subs.push(api.app.client.onNotification('client/resetViewLocations', resolve)));
  try {
    dialog = awaitDialog('Reset Perspective');
    run = vscode.commands.executeCommand('requality.perspective.reset');
    shell = await dialog;
    await pressDefault(shell);
    await run;
    // VS Code resets the view locations and the views of the Requality perspective are shown again
    assert.equal((await reset).perspective, 'com.unitesk.requality.main.perspective');
    await waitFor('the views of the perspective shown', () => shown.includes('requality.outline') && shown.includes('requality.links') ? true : undefined);
    // and its layout is applied again
    await waitFor('the layout applied again', () => layout.applied.length > appliedBefore ? true : undefined);
  } finally {
    subs.forEach(s => s.dispose());
  }
});

test('Window > Perspective > Customize Perspective... opens the Eclipse dialog with its four tabs', async () => {
  const dialog = awaitDialog('Customize Perspective - Requality');
  const run = vscode.commands.executeCommand('requality.perspective.customize');
  const shell = await dialog;
  const snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  for (const tab of ['Toolbar Visibility', 'Menu Visibility', 'Action Set Availability', 'Shortcuts']) {
    assert.ok(snap.includes(tab), `${tab} in ${snap.slice(0, 1500)}`);
  }
  await cancel(shell);
  await run;
});

test('Team > Share Project... and Apply Patch... open their Eclipse wizards', async () => {
  const other = await waitFor('the other project', async () => (await children()).find(r => r.label === 'Other'));
  let dialog = awaitDialog('Share Project');
  let run = vscode.commands.executeCommand('requality.team.shareProject', other);
  let shell = await dialog;
  let snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('Select a repository type'), snap.slice(0, 2000));
  await cancel(shell);
  await run;
  const file = (await children(other)).find(c => c.label === 'notes.txt');
  assert.ok(file);
  dialog = awaitDialog('Apply Patch');
  run = vscode.commands.executeCommand('requality.team.applyPatch', file);
  shell = await dialog;
  snap = JSON.stringify(await api.app.client.request('ui/snapshot', { shell }));
  assert.ok(snap.includes('Patch Input Specification'), snap.slice(0, 2000));
  await cancel(shell);
  await run;
});

test('Rename... of a project runs the Requality rename refactoring', async () => {
  const dialog = awaitDialog('Rename Resource');
  const run = vscode.commands.executeCommand('requality.renameProject', project);
  await typeAndConfirm(await dialog, 'ETSI-renamed');
  await run;
  // ProjectRenameParticipant renames the Requality project; the explorer shows the new name.
  await waitFor('renamed project in the explorer', async () => {
    const roots = await children();
    return roots.some(r => r.label === 'ETSI-renamed') ? true : undefined;
  }, 120000);
});

// ---- The Requality Explorer as the Eclipse view (setting requality.explorer.view = eclipse, the default): the tree
// of the plug-in's view part in the webview view requality.explorerView.

type TreeOp = Op & { parent?: string | null };

/** The view focus of the extension's own module instance (the context key requality.focusedView). */
function viewFocus(): { view: string } {
  return extensionModule('ui', 'rswtHost').viewFocus as { view: string };
}

let explorerShell = '';

async function explorerOps(): Promise<TreeOp[]> {
  return (await api.app.client.request<{ ops: TreeOp[] }>('ui/snapshot', { shell: explorerShell })).ops;
}

async function explorerTree(): Promise<string> {
  const tree = (await explorerOps()).find(o => o.type === 'tree');
  assert.ok(tree, 'no tree in the Eclipse view');
  return tree.id;
}

/** The first text of a tree item. */
function itemText(o: TreeOp): string {
  const texts = o.props?.texts as string[] | undefined;
  return String(texts?.[0] ?? o.props?.text ?? '');
}

/** A tree item of the Eclipse view whose text contains the part (under the parent item, if given). */
async function explorerItem(part: string, parent?: string): Promise<TreeOp> {
  return waitFor(`the item "${part}" of the Eclipse view`, async () =>
    (await explorerOps()).find(o => o.type === 'treeitem' && itemText(o).includes(part) && (!parent || o.parent === parent)));
}

/** The label of the catalog project (renamed by the test of Rename...). */
async function catalogLabel(): Promise<string> {
  const roots = await waitFor('the catalog project', async () => (await children()).find(r => r.kind === 'project'
    && (r.label === 'ETSI-renamed' || String(r.label).includes('TS103096'))));
  return String(roots.label);
}

/** Puts the focus in the webview of the Eclipse view (as a click in it): the explorer becomes the active part. */
async function focusEclipseExplorer(): Promise<void> {
  // the focus can be lost when it moves between the windows of the webviews (a slow X server): it is given again
  for (let attempt = 1; ; attempt++) {
    await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
    await vscode.commands.executeCommand('requality.explorerView.focus');
    try {
      await waitFor('the focus in the Eclipse view', () => viewFocus().view === 'requality.explorerView' ? true : undefined, 10000);
      return;
    } catch (e) {
      if (attempt === 3) {
        throw e;
      }
    }
  }
}

async function treeEvent(type: string, data: Record<string, unknown>): Promise<void> {
  api.app.client.notify('ui/event', { id: await explorerTree(), type, data });
}

async function selectInTree(item: string): Promise<void> {
  await treeEvent('selection', { selection: [item], item });
}

test('the Eclipse view of the Requality Explorer shows the tree of the plug-in', async () => {
  const restarted = waitFor('the server restarted', () => api.app.client.state !== 'ready' ? true : undefined, 30000);
  await vscode.workspace.getConfiguration('requality').update('explorer.view', 'eclipse', vscode.ConfigurationTarget.Global);
  await restarted;
  await waitFor('server ready', () => api.app.client.state === 'ready', 180000);
  await vscode.commands.executeCommand('requality.explorerView.focus');
  explorerShell = await waitFor('the shell of the Eclipse view', () => api.app.ui.shellOfView('requality.explorerView'), 120000);
  const projectItem = await explorerItem(await catalogLabel());
  assert.ok(String(projectItem.props?.image ?? (projectItem.props?.images as string[] | undefined)?.[0] ?? '').endsWith('.png'),
    JSON.stringify(projectItem.props));
});

test('in the Eclipse view a click selects for the workbench and a double click opens the Document Editor', async () => {
  await waitFor('project ready', async () => (await projects()).every(p => p.state === 'ready') ? true : undefined, 240000);
  const projectItem = await explorerItem(await catalogLabel());
  await treeEvent('expand', { item: projectItem.id });
  const documents = await explorerItem('Documents', projectItem.id);
  await treeEvent('expand', { item: documents.id });
  const doc = await waitFor('a document', async () => (await explorerOps()).find(o => o.type === 'treeitem' && o.parent === documents.id));
  await selectInTree(doc.id);
  await waitFor('the selection of the Eclipse view', () => api.app.selection.value.source === 'explorer' && api.app.selection.value.nodes.length === 1 ? true : undefined);
  const before = panelTabs(() => true).length;
  await treeEvent('default', { selection: [doc.id], item: doc.id });
  const tabs = await waitFor('the Document Editor', () => {
    const t = panelTabs(() => true);
    return t.length > before ? t.slice(before) : undefined;
  });
  await closeTabs(tabs);
});

test('the popup menu of the Eclipse view is the one of the plug-in: Copy Full Name', async () => {
  const projectItem = await explorerItem(await catalogLabel());
  const requirements = await explorerItem('Requirements', projectItem.id);
  await selectInTree(requirements.id);
  await vscode.env.clipboard.writeText('');
  await treeEvent('menudetect', { x: 10, y: 10 });
  const copy = await waitFor('Copy Full Name in the popup', async () =>
    (await explorerOps()).find(o => o.type === 'menuitem' && String(o.props?.text ?? '').replace(/&/g, '').split('\t')[0] === 'Copy Full Name'));
  api.app.client.notify('ui/event', { id: copy.id, type: 'selection', data: {} });
  const text = await waitFor('clipboard', async () => (await vscode.env.clipboard.readText()) || undefined);
  assert.ok(text.includes('Requirements'), text);
});

test('F2 in the Eclipse view runs the rename of the explorer part for its selection', async () => {
  const projectItem = await explorerItem(await catalogLabel());
  await selectInTree(projectItem.id);
  await waitFor('the project selected', () => api.app.selection.value.items?.some(i => i.startsWith('project:')) ? true : undefined);
  // F2 is a key of the view: its webview has the focus, which makes the explorer the active part
  await focusEclipseExplorer();
  const dialog = awaitDialog('Rename Resource');
  // the key pressed in the webview of the view: VS Code runs its key binding (requality.focusedView)
  assert.ok(await api.app.ui.postKey('requality.explorerView', { type: 'keydown', key: 'F2', code: 'F2', keyCode: 113 }));
  const shell = await dialog;
  await cancel(shell);
  // the command of the key binding, run without arguments as VS Code runs it for a key
  const again = awaitDialog('Rename Resource');
  const run = vscode.commands.executeCommand('requality.rename');
  await cancel(await again);
  await run;
});

test('Delete in the Eclipse view asks to delete the selected project (Delete Resources)', async () => {
  const label = await catalogLabel();
  const projectItem = await explorerItem(label);
  await selectInTree(projectItem.id);
  await focusEclipseExplorer();
  const dialog = awaitDialog('Delete Resources');
  // the key of org.eclipse.ui.edit.delete (Cmd+Backspace on macOS, as the key binding declares)
  const key = process.platform === 'darwin' ? { key: 'Backspace', code: 'Backspace', keyCode: 8, metaKey: true } : { key: 'Delete', code: 'Delete', keyCode: 46 };
  assert.ok(await api.app.ui.postKey('requality.explorerView', { type: 'keydown', ...key }));
  await cancel(await dialog);
  assert.ok((await children()).some(r => String(r.label) === label), 'the project is kept');
});

test('Collapse All of the toolbar of the Eclipse view collapses its tree', async () => {
  const projectItem = await explorerItem(await catalogLabel());
  await waitFor('the project expanded', async () => (await explorerOps()).find(o => o.id === projectItem.id)?.props?.expanded ? true : undefined);
  await vscode.commands.executeCommand('requality.explorer.collapseAll');
  await waitFor('the project collapsed', async () => (await explorerOps()).find(o => o.id === projectItem.id)?.props?.expanded ? undefined : true);
});

test('Show View of the Requality Explorer focuses the Eclipse view, the context of its key bindings', async () => {
  await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
  await vscode.commands.executeCommand('requality.showView.explorer');
  try {
    await waitFor('the focus in the Eclipse view', () => viewFocus().view === 'requality.explorerView' ? true : undefined, 10000);
  } catch {
    // given again, as a user clicks the view again
    await focusEclipseExplorer();
  }
  await vscode.commands.executeCommand('workbench.view.explorer');
  await waitFor('the focus out of the Eclipse view', () => viewFocus().view === '' ? true : undefined, 30000);
});

/** The first tests start the extension and load the catalog: they always run. */
const SETUP_TESTS = 3;

export async function run(): Promise<void> {
  const failures: string[] = [];
  // REQUALITY_IT_GREP: only the tests whose names match (and the setup tests).
  const grep = process.env.REQUALITY_IT_GREP ? new RegExp(process.env.REQUALITY_IT_GREP) : undefined;
  const selected = tests.filter((x, i) => !grep || i < SETUP_TESTS || grep.test(x.name));
  for (const t of selected) {
    const start = Date.now();
    try {
      // A test that hangs fails after 150 s instead of blocking the suite.
      await Promise.race([t.body(), new Promise((_, reject) => setTimeout(() => reject(new Error('test timed out')), 150000))]);
      console.log(`  ✓ ${t.name} (${Date.now() - start} ms)`);
    } catch (e) {
      failures.push(t.name);
      console.log(`  ✗ ${t.name}\n    ${e instanceof Error ? e.stack ?? e.message : e}`);
      const pid = (api?.app.client as unknown as { proc?: { pid?: number } } | undefined)?.proc?.pid;
      if (pid && /timed out/i.test(String(e))) {
        try {
          const dump = execFileSync('jstack', [String(pid)], { encoding: 'utf8', timeout: 30000 });
          console.log('    --- server threads ---');
          for (const block of dump.split('\n\n').filter(b => /com\.unitesk/.test(b)).slice(0, 40)) {
            console.log(block.split('\n').slice(0, 45).map(l => '    # ' + l).join('\n'));
          }
        } catch (x) {
          console.log(`    (no thread dump: ${x})`);
        }
      }
      console.log('    --- extension log (last lines) ---');
      for (const l of logLines.slice(-80)) {
        console.log(`    | ${l.length > 300 ? l.slice(0, 300) + '...' : l}`);
      }
      if (!api) {
        break;
      }
    }
  }
  console.log(`${selected.length - failures.length} passed, ${failures.length} failed`);
  if (api) {
    console.log('--- Requality log ---');
  }
  if (failures.length) {
    throw new Error(`${failures.length} integration test(s) failed: ${failures.join(', ')}`);
  }
}
