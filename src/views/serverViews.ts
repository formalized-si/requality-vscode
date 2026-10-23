import * as vscode from 'vscode';
import { App } from '../app';
import { ECLIPSE_EXPLORER_VIEW } from './explorerMode';

/** Webview views whose content is an Eclipse view part of the server (rendered remote SWT). */
const SERVER_VIEWS = [ECLIPSE_EXPLORER_VIEW, 'requality.todo', 'requality.links', 'requality.outline', 'requality.bookmarks', 'requality.tasks', 'requality.history', 'requality.synchronize', 'requality.problems', 'requality.progress'];

/**
 * Registers the webview views that show Eclipse views of the server (Update Processor Tasks...): the server creates
 * the view part when the view is first shown (views/open) and keeps its shell for later.
 */
export function registerServerViews(app: App): void {
  const { context, client, ui } = app;
  const resolved = new Set<string>();
  const open = (id: string) => {
    if (client.state === 'ready') {
      void client.request('views/open', { view: id }).catch(e => client.log.appendLine(`[views] ${id}: ${e}`));
    }
  };
  for (const id of SERVER_VIEWS) {
    context.subscriptions.push(vscode.window.registerWebviewViewProvider(id, {
      resolveWebviewView(view) {
        ui.registerView(id, view);
        resolved.add(id);
        open(id);
      },
    }, { webviewOptions: { retainContextWhenHidden: true } }));
  }
  context.subscriptions.push(client.onDidChangeState(s => {
    if (s === 'ready') {
      for (const id of resolved) {
        open(id);
      }
    }
  }));
}
