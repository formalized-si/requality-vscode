import * as vscode from 'vscode';
import { RequalityClient } from '../server/connection';

interface Begin {
  token: string;
  title: string;
  message?: string;
  cancellable: boolean;
  location: 'notification' | 'window';
}

interface Report {
  token: string;
  message?: string;
  percent?: number;
}

interface Active {
  title: string;
  message?: string;
  percent?: number;
}

/** The running operations shown in the status bar (exported for the tests). */
export const progressState = { titles: [] as string[] };

/** The text of the progress area for the running operations (the last started one, and the number of the others). */
export function progressText(active: Active[]): string {
  if (active.length === 0) {
    return '';
  }
  const last = active[active.length - 1];
  const percent = typeof last.percent === 'number' && last.percent > 0 ? ` ${Math.min(100, Math.round(last.percent))}%` : '';
  const others = active.length > 1 ? ` (+${active.length - 1})` : '';
  return `$(loading~spin) ${last.title}${percent}${others}`;
}

/**
 * The progress area of the Eclipse status line (ProgressRegion): the running jobs of the server (progress/begin,
 * progress/report, progress/end) in a status bar item with the name and the progress of the last started one; its
 * tool tip lists every operation with its task, and a click opens the Progress view, where an operation is cancelled
 * with its stop button (as the progress area of Eclipse opens the Progress view). Jobs run in the background, as with
 * Eclipse's "Always run in background" (the default); otherwise the server opens the progress dialog of a user job.
 */
export function registerProgress(client: RequalityClient): vscode.Disposable[] {
  const active = new Map<string, Active>();
  const item = vscode.window.createStatusBarItem('requality.progress', vscode.StatusBarAlignment.Right, 100);
  item.name = 'Requality Progress';
  item.command = 'requality.showView.progress';

  const render = () => {
    const list = [...active.values()];
    progressState.titles = list.map(a => a.title);
    if (list.length === 0) {
      item.hide();
      return;
    }
    item.text = progressText(list);
    const lines = list.map(a => {
      const percent = typeof a.percent === 'number' && a.percent > 0 ? ` (${Math.min(100, Math.round(a.percent))}%)` : '';
      return `${a.title}${percent}${a.message && a.message !== a.title ? `: ${a.message}` : ''}`;
    });
    item.tooltip = `${lines.join('\n')}\n\nOpen the Progress view`;
    item.show();
  };

  return [
    item,
    client.onNotification('progress/begin', (p: Begin) => {
      if (active.has(p.token)) {
        return;
      }
      active.set(p.token, { title: p.title, message: p.message });
      render();
    }),
    client.onNotification('progress/report', (p: Report) => {
      const a = active.get(p.token);
      if (!a) {
        return;
      }
      if (p.message) {
        a.message = p.message;
      }
      if (typeof p.percent === 'number') {
        a.percent = p.percent;
      }
      render();
    }),
    client.onNotification('progress/end', (p: { token: string }) => {
      if (active.delete(p.token)) {
        render();
      }
    }),
    client.onDidChangeState(s => {
      if (s !== 'ready') {
        active.clear();
        render();
      }
    }),
  ];
}
