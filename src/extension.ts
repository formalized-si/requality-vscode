import * as vscode from 'vscode';
import { App, SelectionHub } from './app';
import { activateCore } from './features/core';
import { activateMcp } from './features/mcp';
import { activateCompare } from './features/compare';
import { activateServerCommands } from './features/serverCommands';
import { activateWorkbench } from './features/workbench';
import { registerDialogHandlers } from './host/dialogs';
import { registerProgress } from './host/progress';
import { RequalityClient } from './server/connection';
import { RequalityExplorer } from './views/explorer';
import { ECLIPSE_EXPLORER_VIEW } from './views/explorerMode';
import { RswtHost } from './ui/rswtHost';
import { registerPropertiesView } from './views/properties';
import { registerServerViews } from './views/serverViews';

let app: App | undefined;

/** Exposed for the integration tests. */
export interface RequalityApi {
  app: App;
}

export async function activate(context: vscode.ExtensionContext): Promise<RequalityApi> {
  const log = vscode.window.createOutputChannel('Requality');
  context.subscriptions.push(log);
  const client = new RequalityClient(context, log);
  context.subscriptions.push(client);
  context.subscriptions.push(...registerDialogHandlers(client));
  context.subscriptions.push(...registerProgress(client));
  const explorer = new RequalityExplorer(client);
  context.subscriptions.push(explorer);
  const selection = new SelectionHub();
  context.subscriptions.push(selection);
  const ui = new RswtHost(client, context);
  context.subscriptions.push(ui);
  // the Eclipse view of the explorer shows the title tool tip of the part as the native tree does
  context.subscriptions.push(explorer.onDidChangeTitleToolTip(t => ui.setViewDescription(ECLIPSE_EXPLORER_VIEW, t)));
  app = { context, client, log, explorer, selection, ui };
  registerPropertiesView(app);
  registerServerViews(app);

  activateCore(app);
  activateMcp(app);
  activateCompare(app);
  activateWorkbench(app);
  // Last: commands not implemented by a feature module run their Eclipse command, wizard or menu on the server.
  await activateServerCommands(app);

  context.subscriptions.push(vscode.workspace.onDidChangeWorkspaceFolders(() => {
    if (client.state === 'ready') {
      const folders = (vscode.workspace.workspaceFolders ?? []).filter(f => f.uri.scheme === 'file').map(f => f.uri.fsPath);
      void client.request('workspace/setFolders', { folders });
    }
  }));
  // New or removed catalogs (a .project appears or disappears) are picked up like an Eclipse workspace refresh.
  const projectWatcher = vscode.workspace.createFileSystemWatcher('**/.project');
  let rescanTimer: NodeJS.Timeout | undefined;
  const rescan = () => {
    if (rescanTimer) {
      clearTimeout(rescanTimer);
    }
    rescanTimer = setTimeout(() => {
      if (client.state === 'ready') {
        void client.request('workspace/rescan');
      }
    }, 300);
  };
  projectWatcher.onDidCreate(rescan);
  projectWatcher.onDidDelete(rescan);
  context.subscriptions.push(projectWatcher);
  // The watcher reports nothing that happened before it was watching (it starts asynchronously): once the server is
  // ready the workspace is scanned again after the watcher had time to start.
  context.subscriptions.push(client.onDidChangeState(s => {
    if (s === 'ready') {
      setTimeout(rescan, 3000);
    }
  }));

  void client.start().catch(e => {
    void vscode.window.showErrorMessage(`Requality server could not start: ${e instanceof Error ? e.message : e}`, 'Show Log')
      .then(c => {
        if (c === 'Show Log') {
          log.show();
        }
      });
  });
  return { app };
}

export async function deactivate(): Promise<void> {
  if (app) {
    await app.client.stop();
    app = undefined;
  }
}
