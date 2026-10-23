import * as vscode from 'vscode';
import { App, command } from '../app';
import { commandItems, executeServerCommand } from './serverCommands';
import { ExplorerItem } from '../views/explorer';
import { explorerViewMode } from '../views/explorerMode';

/** Explorer basics, Open, filters, link with editor and server control. */
export function activateCore(app: App): void {
  const { context, client, explorer, selection } = app;
  const subs = context.subscriptions;

  subs.push(command(app, 'requality.refresh', async () => {
    await client.request('workspace/rescan');
    await explorer.refresh(null);
  }));

  // CommonNavigator open (double click / Enter): the OPEN action of the explorer's action providers (OpenAction).
  subs.push(command(app, 'requality.open', async (item?: ExplorerItem | string, items?: ExplorerItem[]) => {
    const ids = commandItems(item, items) ?? explorer.selection.map(i => i.id);
    // OpenActionProvider: files open with OpenFileAction (in the editor chosen with Open With, else the default one).
    const files = ids.map(id => explorer.item(id)).filter((i): i is ExplorerItem => i?.kind === 'resource' && i.icon === 'file');
    if (files.length) {
      const list = await client.request<Array<{ label?: string; index?: number; children?: unknown[] }>>('menu/items',
        { menu: 'com.unitesk.requality.views.RequalityExplorer.popup', nodes: files.map(f => f.id), source: 'explorer' });
      const open = list.find(i => i.label === 'Open' && !i.children && i.index !== undefined);
      if (open) {
        await client.request('menu/run', { menu: 'com.unitesk.requality.views.RequalityExplorer.popup', index: open.index });
      }
      return;
    }
    if (ids.length) {
      await client.request('explorer/open', { nodes: ids });
    }
  }));

  // org.eclipse.ui.edit.rename (F2): a project is renamed by the navigator's rename action, a document folder by
  // RenameDocFolderHandler.
  subs.push(command(app, 'requality.rename', async (item?: ExplorerItem) => {
    if (!(item && typeof item === 'object' && 'kind' in item) && explorerViewMode() === 'eclipse') {
      // the key binding of the Eclipse view: the handler of the explorer part for its selection (the rename action of
      // the navigator for resources, RenameDocFolderHandler for document folders)
      await executeServerCommand(app, 'org.eclipse.ui.edit.rename');
      return;
    }
    const target = item && typeof item === 'object' && 'kind' in item ? item : explorer.selection[0];
    if (!target) {
      return;
    }
    await vscode.commands.executeCommand(target.kind === 'node' ? 'requality.renameDocFolder' : 'requality.renameProject', target);
  }));

  subs.push(command(app, 'requality.restartServer', () => client.restart()));
  // CollapseAllHandler of the Common Navigator (the toolbar of the Eclipse view)
  subs.push(command(app, 'requality.explorer.collapseAll', () => client.request('explorer/collapseAll')));
  // the server shows the explorer as the setting asks when it starts
  subs.push(vscode.workspace.onDidChangeConfiguration(e => {
    if (e.affectsConfiguration('requality.explorer.view') && (client.state === 'ready' || client.state === 'failed')) {
      void client.restart().catch(err => app.log.appendLine(`[explorer] restart: ${err}`));
    }
  }));
  subs.push(command(app, 'requality.showLog', () => app.log.show()));

  // ---- filters (CNF "Filters and Customization"): the dialog of the server changes the settings, which it gets back
  subs.push(client.onNotification('client/updateSetting', (p: { key: string; value: unknown }) => {
    // the settings of the server's preference pages: requality.* (navigator filters), and workbench.* of VS Code
    // (General > Open mode = workbench.list.openMode)
    const config = p.key.startsWith('workbench.') ? vscode.workspace.getConfiguration() : vscode.workspace.getConfiguration('requality');
    void config.update(p.key, p.value, vscode.ConfigurationTarget.Global);
  }));
  subs.push(vscode.workspace.onDidChangeConfiguration(e => {
    if (e.affectsConfiguration('workbench.list.openMode') && client.state === 'ready') {
      client.notify('settings/listOpenMode', { value: vscode.workspace.getConfiguration('workbench.list').get('openMode') });
    }
  }));
  const pushFilters = async () => {
    if (client.state === 'ready') {
      const c = vscode.workspace.getConfiguration('requality');
      await client.request('explorer/setFilters', {
        filters: c.get('explorer.filters') ?? {},
        contentExtensions: c.get('explorer.contentExtensions') ?? {},
        userFilters: c.get('explorer.userFilters') ?? [],
      });
      await explorer.refresh(null);
    }
  };
  subs.push(client.onDidChangeState(s => {
    if (s === 'ready') {
      void pushFilters();
    }
  }));
  subs.push(vscode.workspace.onDidChangeConfiguration(e => {
    if (e.affectsConfiguration('requality.explorer.filters') || e.affectsConfiguration('requality.explorer.contentExtensions')
      || e.affectsConfiguration('requality.explorer.userFilters')) {
      void pushFilters();
    }
  }));
  // ---- selection: explorer and editors feed Properties (and the link with editor)
  const linked = () => vscode.workspace.getConfiguration('requality').get<boolean>('explorer.linkWithEditor', true);
  const setLinkedContext = () => {
    void vscode.commands.executeCommand('setContext', 'requality.linked', linked());
    if (client.state === 'ready') {
      void client.request('explorer/setLinked', { linked: linked() }).catch(() => undefined);
    }
  };
  subs.push(client.onDidChangeState(s => {
    if (s === 'ready') {
      setLinkedContext();
    }
  }));
  setLinkedContext();
  subs.push(vscode.workspace.onDidChangeConfiguration(e => {
    if (e.affectsConfiguration('requality.explorer.linkWithEditor')) {
      setLinkedContext();
    }
  }));
  subs.push(command(app, 'requality.linkWithEditor', () =>
    vscode.workspace.getConfiguration('requality').update('explorer.linkWithEditor', true, vscode.ConfigurationTarget.Global)));
  subs.push(command(app, 'requality.unlinkWithEditor', () =>
    vscode.workspace.getConfiguration('requality').update('explorer.linkWithEditor', false, vscode.ConfigurationTarget.Global)));

  // The selection of the tree of the Eclipse view (made in its webview, or by the server).
  subs.push(client.onNotification('explorer/selection', (p: { items: string[]; nodes: string[] }) => {
    selection.set('explorer', p.nodes, p.items);
  }));
  // The explorer selection is the workbench selection of the explorer part; the server links it with the editors
  // (LinkHelperService) and mirrors other parts' selections back (RequalityCNF), without echoing them.
  subs.push(explorer.onDidSelect(items => {
    const nodes = items.map(i => (i.kind === 'project' ? i.node : i.kind === 'node' ? i.id : undefined)).filter((x): x is string => !!x);
    selection.set('explorer', nodes, items.map(i => i.id));
    if (explorer.consumeServerSelection(items)) {
      return;
    }
    void client.request('selection/set', { source: 'explorer', nodes: items.map(i => i.id) }).catch(() => undefined);
  }));
}
