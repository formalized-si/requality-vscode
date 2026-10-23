import * as vscode from 'vscode';
import { App, run } from '../app';
import { SERVER_TARGETS, ServerTarget } from '../generated/commandMap';
import { ExplorerItem } from '../views/explorer';
import { explorerViewId } from '../views/explorerMode';

/** An item of a dynamic menu (MenuService). */
interface MenuItem {
  index?: number;
  label?: string;
  enabled?: boolean;
  checked?: boolean;
  tooltip?: string;
  separator?: boolean;
  children?: MenuItem[];
  /** Node type of a New menu item (NewTypedTemplatedMenuPoint). */
  type?: string;
}

interface CommandState {
  handled: boolean;
  enabled: boolean;
  label?: string;
  checked?: boolean;
}

/** The Requality Explorer context menu filled by the navigator action providers (server MenuService). */
const EXPLORER_POPUP = 'com.unitesk.requality.views.RequalityExplorer.popup';
/** The view menu of the Requality Explorer filled by the action providers (working sets, filters). */
const EXPLORER_VIEW_MENU = 'com.unitesk.requality.views.RequalityExplorer.viewMenu';
/** The dynamic New menu (plugin.xml dynamic NewMenuPoint). */
const NEW_MENU = 'com.unitesk.requality.CreateDynNewMenu';

/**
 * Whether a popup item has one of the labels of an 'action' or 'submenu' target ("|" separates alternatives, a
 * trailing "*" matches a prefix: texts that depend on the selection).
 */
export function matchesLabel(item: MenuItem, target: string): boolean {
  const label = item.label ?? '';
  return target.split('|').some(t => {
    const l = t.replace(/&/g, '');
    return l.endsWith('*') ? label.startsWith(l.slice(0, -1)) : label === l;
  });
}

/**
 * The item of a menu that a target names: its label, or "Submenu>Label" for an item of a submenu (e.g. New>Folder);
 * {@code submenu}: the target is a submenu (an item with children).
 */
export function findTarget(items: MenuItem[], target: string, submenu: boolean): MenuItem | undefined {
  const path = target.split('>');
  let level = items;
  for (let i = 0; i < path.length - 1; i++) {
    const sub = level.find(it => matchesLabel(it, path[i]) && it.children);
    if (!sub?.children) {
      return undefined;
    }
    level = sub.children;
  }
  return level.find(it => matchesLabel(it, path[path.length - 1]) && !!it.children === submenu);
}

/** The Eclipse view id of the Requality Explorer, shown by the view of the setting requality.explorer.view. */
export const ECLIPSE_EXPLORER_ID = 'com.unitesk.requality.views.RequalityExplorer';

/** Eclipse view ids of the views the extension shows (besides the Requality Explorer). */
const VIEW_IDS: Record<string, string> = {
  'com.unitesk.requality.views.RequalityLinksExplorer': 'requality.links',
  'com.unitesk.requality.views.ToDoList': 'requality.todo',
  'org.eclipse.ui.views.PropertySheet': 'requality.properties',
  'org.eclipse.ui.views.ContentOutline': 'requality.outline',
  'org.eclipse.ui.views.BookmarkView': 'requality.bookmarks',
  'org.eclipse.ui.views.TaskList': 'requality.tasks',
  'org.eclipse.ui.views.ProblemView': 'requality.problems',
  'org.eclipse.team.ui.GenericHistoryView': 'requality.history',
  'org.eclipse.team.sync.views.SynchronizeView': 'requality.synchronize',
  'org.eclipse.ui.views.ProgressView': 'requality.progress',
};

/**
 * The selection items a command applies to, as the selection source knows them (project items keep their
 * "project:" id): the clicked item and the multi-selection VS Code passes, else the current selection.
 */
export function commandItems(item?: ExplorerItem | string, items?: ExplorerItem[]): string[] | undefined {
  if (Array.isArray(items) && items.length > 0) {
    return items.map(i => i.id);
  }
  if (typeof item === 'string') {
    return [item];
  }
  if (item && typeof item === 'object' && 'id' in item && 'kind' in item) {
    return [item.id];
  }
  return undefined;
}

/** Runs an Eclipse command on the server for the given items (or the current selection). */
export async function executeServerCommand(app: App, id: string, params?: Record<string, string>, items?: string[],
  toolbarView?: string): Promise<unknown> {
  return app.client.request('command/execute', {
    command: id,
    params: params ?? {},
    nodes: items,
    source: items ? app.selection.value.source : undefined,
    // a button of a view toolbar: its ToolItem is the trigger of the command (HandledContributionItem)
    ...(toolbarView ? { toolbarView } : {}),
  });
}

/** The first item of a menu (searching submenus too) that matches. */
function findItem(items: MenuItem[], match: (i: MenuItem) => boolean): MenuItem | undefined {
  for (const i of items) {
    if (match(i)) {
      return i;
    }
    const c = i.children ? findItem(i.children, match) : undefined;
    if (c) {
      return c;
    }
  }
  return undefined;
}

function flatten(items: MenuItem[], prefix = ''): Array<vscode.QuickPickItem & { index?: number }> {
  const out: Array<vscode.QuickPickItem & { index?: number }> = [];
  for (const i of items) {
    if (i.separator) {
      out.push({ label: '', kind: vscode.QuickPickItemKind.Separator });
    } else if (i.children) {
      out.push({ label: prefix + (i.label ?? ''), kind: vscode.QuickPickItemKind.Separator });
      out.push(...flatten(i.children, prefix + (i.label ?? '') + ' / '));
    } else if (i.index !== undefined && i.enabled !== false) {
      out.push({
        label: (i.checked ? '$(check) ' : '') + prefix + (i.label ?? ''),
        detail: i.tooltip,
        index: i.index,
      });
    }
  }
  return out;
}

/** Shows a dynamic menu of the server (an Eclipse {@code <dynamic>} contribution) in a quick pick and runs the pick. */
export async function showServerMenu(app: App, menu: string, title: string, items?: string[]): Promise<void> {
  const list = await app.client.request<MenuItem[]>('menu/items', { menu, nodes: items, source: items ? app.selection.value.source : undefined });
  const picks = flatten(list);
  if (!picks.some(p => p.index !== undefined)) {
    void vscode.window.showInformationMessage(`${title}: no items for the selection.`);
    return;
  }
  const pick = await vscode.window.showQuickPick(picks, { title, placeHolder: title });
  if (pick?.index !== undefined) {
    await app.client.request('menu/run', { menu, index: pick.index });
  }
}

/** The VS Code view of an Eclipse view id (or the VS Code id itself). */
export function vscodeViewId(viewId: string): string {
  return viewId === ECLIPSE_EXPLORER_ID ? explorerViewId() : VIEW_IDS[viewId] ?? viewId;
}

/** Shows a view by its Eclipse or VS Code id. */
export async function showView(viewId: string): Promise<void> {
  const id = vscodeViewId(viewId);
  await vscode.commands.executeCommand(`${id}.focus`);
}

/** IWorkbenchPage.hideView: the VS Code view of the part is hidden (the Hide command VS Code has for each view). */
export async function hideView(viewId: string): Promise<void> {
  const id = vscodeViewId(viewId);
  if ((await vscode.commands.getCommands(true)).includes(`${id}.removeView`)) {
    await vscode.commands.executeCommand(`${id}.removeView`);
  }
}

/**
 * Commands that run Eclipse commands, wizards and dynamic menus on the server (src/generated/commandMap.ts), the
 * enablement of commands from their Eclipse handlers, and the workbench requests of the server (show view, open
 * URL, clipboard).
 */
export async function activateServerCommands(app: App): Promise<void> {
  const { client, context } = app;
  const subs = context.subscriptions;
  const registered = new Set(await vscode.commands.getCommands(true));
  const titles = new Map<string, string>();
  const pkgCommands = (context.extension.packageJSON?.contributes?.commands ?? []) as Array<{ command: string; title: string }>;
  for (const c of pkgCommands) {
    titles.set(c.command, c.title);
  }

  for (const [vsId, target] of Object.entries(SERVER_TARGETS)) {
    if (registered.has(vsId)) {
      continue; // implemented by a feature module
    }
    subs.push(vscode.commands.registerCommand(vsId, run(app, titles.get(vsId) ?? vsId, async (item?: ExplorerItem | string, items?: ExplorerItem[]) => {
      const selected = commandItems(item, items);
      await runTarget(app, target, titles.get(vsId) ?? vsId, selected);
      scheduleState();
    })));
  }

  // ---- enablement: requality.enabled.<command> mirrors the Eclipse handler state for the selection
  const byEclipseId = new Map<string, string[]>();
  for (const [vsId, t] of Object.entries(SERVER_TARGETS)) {
    if (t.kind === 'command') {
      const l = byEclipseId.get(t.id) ?? [];
      l.push(vsId);
      byEclipseId.set(t.id, l);
    }
  }
  const actions = Object.entries(SERVER_TARGETS).filter(([, t]) => t.kind === 'action' || t.kind === 'submenu');
  const viewActions = Object.entries(SERVER_TARGETS).filter(([, t]) => t.kind === 'viewAction' || t.kind === 'viewSubmenu' || t.kind === 'viewList');
  let timer: NodeJS.Timeout | undefined;
  const updateState = async () => {
    if (client.state !== 'ready') {
      return;
    }
    try {
      // CNF actions are enabled when the action providers add them to the menu for the selection and isEnabled().
      const list = actions.length ? await client.request<MenuItem[]>('menu/items', { menu: EXPLORER_POPUP }) : [];
      for (const [vsId, t] of actions) {
        const item = findTarget(list, t.id, t.kind === 'submenu');
        const key = vsId.substring('requality.'.length);
        // shown: the action providers put the action in the popup for the selection; enabled: and it is enabled
        void vscode.commands.executeCommand('setContext', 'requality.shown.' + key, item !== undefined);
        void vscode.commands.executeCommand('setContext', 'requality.enabled.' + key, item !== undefined && item.enabled !== false
          && (!item.children || item.children.some(c => c.index !== undefined && c.enabled !== false)));
      }
    } catch (e) {
      app.log.appendLine(`[menu/items] ${e instanceof Error ? e.message : e}`);
    }
    // The view menus (of the explorer, and of the views the server hosts: Bookmarks, Tasks): an item is shown when the
    // view puts it there; enabled and checked as the action.
    const menus = new Map<string, Array<[string, ServerTarget]>>();
    for (const [vsId, t] of viewActions) {
      const menu = t.params?.menu ?? EXPLORER_VIEW_MENU;
      menus.set(menu, [...(menus.get(menu) ?? []), [vsId, t]]);
    }
    for (const [menu, targets] of menus) try {
      const list = await client.request<MenuItem[]>('menu/items', { menu });
      for (const [vsId, t] of targets) {
        const item = t.kind === 'viewList' ? list.find(i => !i.children && new RegExp(t.id).test(i.label ?? ''))
          : findTarget(list, t.id, t.kind === 'viewSubmenu') ?? findItem(list, i => matchesLabel(i, t.id) && (t.kind === 'viewSubmenu') === !!i.children);
        const key = vsId.substring('requality.'.length);
        void vscode.commands.executeCommand('setContext', 'requality.shown.' + key, item !== undefined);
        void vscode.commands.executeCommand('setContext', 'requality.enabled.' + key, item !== undefined && item.enabled !== false
          && (!item.children || item.children.some(c => c.index !== undefined && c.enabled !== false)));
        void vscode.commands.executeCommand('setContext', 'requality.checked.' + key, !!item?.checked);
      }
    } catch (e) {
      // a view part that was not created yet has no menu: its items are not shown
      for (const [vsId] of targets) {
        void vscode.commands.executeCommand('setContext', 'requality.shown.' + vsId.substring('requality.'.length), false);
      }
      if (menu === EXPLORER_VIEW_MENU) {
        app.log.appendLine(`[menu/items view] ${e instanceof Error ? e.message : e}`);
      }
    }
    try {
      const state = await client.request<Record<string, CommandState>>('command/state', { commands: [...byEclipseId.keys()] });
      for (const [eclipseId, s] of Object.entries(state)) {
        for (const vsId of byEclipseId.get(eclipseId) ?? []) {
          void vscode.commands.executeCommand('setContext', 'requality.enabled.' + vsId.substring('requality.'.length), s.enabled);
        }
      }
    } catch (e) {
      app.log.appendLine(`[command/state] ${e instanceof Error ? e.message : e}`);
    }
  };
  const scheduleState = () => {
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(() => void updateState(), 120);
  };
  subs.push(app.selection.onDidChange(scheduleState));
  subs.push(client.onDidChangeState(s => {
    if (s === 'ready') {
      scheduleState();
    }
  }));
  subs.push(client.onNotification('command/changed', () => scheduleState()));
  subs.push({ dispose: () => timer && clearTimeout(timer) });
  if (client.state === 'ready') {
    scheduleState();
  }

  // ---- workbench requests of the server
  subs.push(client.onNotification('client/showView', (p: { id: string }) => void showView(p.id)));
  subs.push(client.onNotification('client/hideView', (p: { id: string }) => void hideView(p.id)));
  subs.push(client.onNotification('client/openExternal', (p: { url: string }) => void vscode.env.openExternal(vscode.Uri.parse(p.url))));
  subs.push(client.onNotification('client/clipboard', (p: { text: string }) => void vscode.env.clipboard.writeText(p.text ?? '')));
  subs.push(client.onRequest('client/readClipboard', () => vscode.env.clipboard.readText()));
  subs.push(client.onNotification('client/setContext', (p: { key: string; value: unknown }) => void vscode.commands.executeCommand('setContext', p.key, p.value)));
  // OpenFileAction / OpenWithMenu: a file opens in the chosen editor ("default": the text editor, "system": the
  // application of the operating system, else the view type of a VS Code custom editor).
  subs.push(client.onNotification('client/openFile', (p: { path: string; editor?: string; line?: number }) => void openFile(app, p.path, p.editor, p.line)));
  // Show In targets of the navigator (ShowInActionProvider).
  subs.push(client.onNotification('client/showIn', (p: { target: string; path: string }) => void showIn(app, p.target, p.path)));
}

async function openFile(app: App, file: string, editor: string | undefined, line?: number): Promise<void> {
  const uri = vscode.Uri.file(file);
  try {
    if (editor === 'system') {
      await vscode.env.openExternal(uri);
    } else if (line !== undefined && line > 0 && (!editor || editor === 'default')) {
      // a marker (bookmark, task) with a line: the text editor shows it (IDE.gotoMarker)
      const position = new vscode.Position(line - 1, 0);
      await vscode.window.showTextDocument(uri, { selection: new vscode.Range(position, position) });
    } else {
      await vscode.commands.executeCommand('vscode.openWith', uri, editor && editor !== 'default' ? editor : 'default');
    }
  } catch (e) {
    app.log.appendLine(`[openFile] ${file} (${editor}): ${e instanceof Error ? e.message : e}`);
    void vscode.window.showErrorMessage(`Could not open ${file}: ${e instanceof Error ? e.message : e}`);
  }
}

async function showIn(app: App, target: string, file: string): Promise<void> {
  const uri = vscode.Uri.file(file);
  try {
    switch (target) {
      case 'explorer':
        await vscode.commands.executeCommand('revealInExplorer', uri);
        break;
      case 'properties':
        await showView('org.eclipse.ui.views.PropertySheet');
        break;
      case 'system':
        await vscode.commands.executeCommand('revealFileInOS', uri);
        break;
      case 'terminal': {
        let cwd = file;
        try {
          if ((await vscode.workspace.fs.stat(uri)).type !== vscode.FileType.Directory) {
            cwd = vscode.Uri.joinPath(uri, '..').fsPath;
          }
        } catch {
          // a missing file: its folder
          cwd = vscode.Uri.joinPath(uri, '..').fsPath;
        }
        const terminal = vscode.window.createTerminal({ cwd });
        terminal.show();
        break;
      }
      default:
        app.log.appendLine(`[showIn] unknown target ${target}`);
    }
  } catch (e) {
    app.log.appendLine(`[showIn] ${target} ${file}: ${e instanceof Error ? e.message : e}`);
  }
}

async function runTarget(app: App, target: ServerTarget, title: string, items: string[] | undefined): Promise<void> {
  switch (target.kind) {
    case 'command':
      await executeServerCommand(app, target.id, target.params, items, target.toolbarView);
      break;
    case 'wizard':
      await app.client.request('wizard/open', { id: target.id, nodes: items, source: items ? app.selection.value.source : undefined });
      break;
    case 'menu':
      await showServerMenu(app, target.id, title, items);
      break;
    case 'newType': {
      // An item of the New submenu (NewMenuPoint): the NewTypedTemplatedMenuPoint action of the node type.
      const list = await app.client.request<MenuItem[]>('menu/items', { menu: NEW_MENU, nodes: items, source: items ? app.selection.value.source : undefined });
      const item = list.find(i => i.type === target.id);
      if (item?.index === undefined) {
        void vscode.window.showInformationMessage(`${title}: cannot be created here.`);
        return;
      }
      await app.client.request('menu/run', { menu: NEW_MENU, index: item.index });
      break;
    }
    case 'viewAction': {
      // An action of the view menu (the "..." menu of the explorer or of a hosted view), found by its text.
      const menu = target.params?.menu ?? EXPLORER_VIEW_MENU;
      const list = await app.client.request<MenuItem[]>('menu/items', { menu });
      const item = findTarget(list, target.id, false) ?? findItem(list, i => matchesLabel(i, target.id) && !i.children);
      if (item?.index === undefined || item.enabled === false) {
        void vscode.window.showInformationMessage(`${title}: not available.`);
        return;
      }
      await app.client.request('menu/run', { menu, index: item.index });
      break;
    }
    case 'viewSubmenu': {
      // A submenu of the view menu (recently used working sets or filters, Show, Group By, Sort By): a quick pick.
      const menu = target.params?.menu ?? EXPLORER_VIEW_MENU;
      const list = await app.client.request<MenuItem[]>('menu/items', { menu });
      const sub = findTarget(list, target.id, true) ?? findItem(list, i => matchesLabel(i, target.id) && !!i.children);
      const picks = sub?.children ? flatten(sub.children) : [];
      if (!picks.some(p => p.index !== undefined)) {
        void vscode.window.showInformationMessage(`${title}: no items.`);
        return;
      }
      const pick = await vscode.window.showQuickPick(picks, { title, placeHolder: title });
      if (pick?.index !== undefined) {
        await app.client.request('menu/run', { menu, index: pick.index });
      }
      break;
    }
    case 'viewList': {
      // Items of the view menu named by a pattern (the recently used working sets "1 name"...): a quick pick.
      const list = await app.client.request<MenuItem[]>('menu/items', { menu: EXPLORER_VIEW_MENU });
      const rx = new RegExp(target.id);
      const picks = flatten(list.filter(i => !i.children && rx.test(i.label ?? '')));
      if (!picks.some(p => p.index !== undefined)) {
        void vscode.window.showInformationMessage(`${title}: no items.`);
        return;
      }
      const pick = await vscode.window.showQuickPick(picks, { title, placeHolder: title });
      if (pick?.index !== undefined) {
        await app.client.request('menu/run', { menu: EXPLORER_VIEW_MENU, index: pick.index });
      }
      break;
    }
    case 'submenu': {
      // A submenu the explorer's action providers fill (Open With, Show In): its items in a quick pick.
      const list = await app.client.request<MenuItem[]>('menu/items', { menu: EXPLORER_POPUP, nodes: items, source: items ? app.selection.value.source : undefined });
      const sub = findTarget(list, target.id, true);
      const picks = sub?.children ? flatten(sub.children) : [];
      if (!picks.some(p => p.index !== undefined)) {
        void vscode.window.showInformationMessage(`${title}: not available for the selection.`);
        return;
      }
      const pick = await vscode.window.showQuickPick(picks, { title: target.id, placeHolder: target.id });
      if (pick?.index !== undefined) {
        await app.client.request('menu/run', { menu: EXPLORER_POPUP, index: pick.index });
      }
      break;
    }
    case 'action': {
      // An action the explorer's action providers put in its context menu (CNFActionProvider), found by its text.
      const list = await app.client.request<MenuItem[]>('menu/items', { menu: EXPLORER_POPUP, nodes: items, source: items ? app.selection.value.source : undefined });
      const item = findTarget(list, target.id, false);
      if (item?.index === undefined || item.enabled === false) {
        app.log.appendLine(`[menu] ${target.id}: ${item ? 'disabled' : 'not in the menu'} for ${JSON.stringify(items)}: ${JSON.stringify(list.map(i => i.label))}`);
        void vscode.window.showInformationMessage(`${title}: not available for the selection.`);
        return;
      }
      await app.client.request('menu/run', { menu: EXPLORER_POPUP, index: item.index });
      break;
    }
  }
}
