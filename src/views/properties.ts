import * as vscode from 'vscode';
import { App } from '../app';

/**
 * The Properties view (Eclipse Properties with the Requality tabbed property sections). Its content is a remote SWT
 * shell of the server, shown in this webview view.
 */
export function registerPropertiesView(app: App): void {
  const { context, client, ui } = app;
  context.subscriptions.push(vscode.window.registerWebviewViewProvider('requality.properties', {
    resolveWebviewView(view) {
      ui.registerView('requality.properties', view);
      if (client.state === 'ready') {
        void client.request('properties/open').catch(e => client.log.appendLine(`[properties] ${e}`));
      }
    },
  }, { webviewOptions: { retainContextWhenHidden: true } }));
  context.subscriptions.push(client.onDidChangeState(s => {
    if (s === 'ready') {
      void client.request('properties/open').catch(e => client.log.appendLine(`[properties] ${e}`));
    }
  }));
  context.subscriptions.push(vscode.commands.registerCommand('requality.showProperties', async () => {
    await vscode.commands.executeCommand('requality.properties.focus');
  }));
}
