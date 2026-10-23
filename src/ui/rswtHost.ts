import * as path from 'path';
import * as vscode from 'vscode';
import { RequalityClient } from '../server/connection';
import { BridgeMessage, handleBridgeMessage } from './bridge';
import { imageUrl } from './images';

interface Op {
  op: 'create' | 'set' | 'dispose' | 'call';
  id: string;
  props?: Record<string, unknown>;
}

interface OpenShell {
  shell: string;
  title: string;
  host: 'panel' | 'view';
  view?: string | null;
}

/** A webview showing one remote SWT shell. */
interface ShellHost {
  shell: string;
  webview: vscode.Webview;
  ready: boolean;
  snapshotSeq: number;
  buffered: Array<{ seq: number; ops: Op[] }>;
  /** Messages to the webview go out in order after their URLs are mapped. */
  queue: Promise<void>;
  panel?: vscode.WebviewPanel;
  /** The webview view that shows the shell (the Eclipse view of a part). */
  view?: string;
  closingFromServer?: boolean;
  dispose(): void;
}

/** The webview view whose content has the focus (exported for the tests). */
export const viewFocus = { view: '' };

const nonce = () => Array.from({ length: 24 }, () => 'abcdefghijklmnopqrstuvwxyz0123456789'[Math.floor(Math.random() * 36)]).join('');

/**
 * Hosts the remote SWT shells of the server (dialogs, wizards, the Properties view) in webviews.
 */
export class RswtHost implements vscode.Disposable {
  private readonly hosts = new Map<string, ShellHost>();
  private readonly views = new Map<string, vscode.WebviewView>();
  private readonly viewShells = new Map<string, string>();
  /** The content descriptions of the server's view parts (WorkbenchPart.setContentDescription), by VS Code view id. */
  private readonly viewDescriptions = new Map<string, string>();
  private readonly subs: vscode.Disposable[] = [];

  constructor(private readonly client: RequalityClient, private readonly context: vscode.ExtensionContext) {
    this.subs.push(client.onNotification('ui/openShell', (p: OpenShell) => this.open(p)));
    this.subs.push(client.onNotification('ui/closeShell', (p: { shell: string }) => this.close(p.shell)));
    this.subs.push(client.onNotification('ui/placeShell', (p: { shell: string; beside?: string }) => this.place(p.shell, p.beside)));
    this.subs.push(client.onNotification('ui/update', (p: { shell: string; seq: number; ops: Op[] }) => this.update(p.shell, p.seq, p.ops)));
    this.subs.push(client.onNotification('client/viewDescription', (p: { view: string; description?: string }) => {
      this.viewDescriptions.set(p.view, p.description ?? '');
      const view = this.views.get(p.view);
      if (view) {
        view.description = p.description || undefined;
      }
    }));
    this.subs.push(client.onDidChangeState(s => {
      if (s !== 'ready') {
        for (const h of [...this.hosts.values()]) {
          h.closingFromServer = true;
          h.dispose();
        }
        this.hosts.clear();
        this.viewShells.clear();
      }
    }));
  }

  private roots(): vscode.Uri[] {
    return [
      vscode.Uri.joinPath(this.context.extensionUri, 'media'),
      vscode.Uri.joinPath(this.context.extensionUri, 'dist', 'server', 'resources'),
      this.context.globalStorageUri,
    ];
  }

  /** Shows a text next to the name of a webview view (the title tool tip or content description of its part). */
  setViewDescription(viewId: string, description: string | undefined): void {
    this.viewDescriptions.set(viewId, description ?? '');
    const view = this.views.get(viewId);
    if (view) {
      view.description = description || undefined;
    }
  }

  /**
   * The context key requality.focusedView names the webview view whose content has the focus: VS Code sets focusedView
   * for the focus inside the element of a view, while a webview is placed over the view, so the key bindings of an
   * Eclipse view (e.g. Delete in the Requality Explorer) take this key.
   */
  private setViewFocus(view: string): void {
    if (viewFocus.view !== view) {
      viewFocus.view = view;
      void vscode.commands.executeCommand('setContext', 'requality.focusedView', view);
    }
  }

  /**
   * Gives a key event to the focused control of a webview view (SWT Display.post): the page and VS Code get it as a key
   * of the user (the integration tests press the keys of an Eclipse view with it).
   */
  postKey(viewId: string, event: { type: 'keydown' | 'keyup'; key: string; code: string; keyCode?: number; ctrlKey?: boolean;
    shiftKey?: boolean; altKey?: boolean; metaKey?: boolean }): Thenable<boolean> {
    const view = this.views.get(viewId);
    return view ? view.webview.postMessage({ type: 'postEvent', event }) : Promise.resolve(false);
  }

  /** The shell the server shows in a webview view (e.g. the Bookmarks view), if it opened one. */
  shellOfView(viewId: string): string | undefined {
    return this.viewShells.get(viewId);
  }

  /** Lets a webview view (e.g. Properties) show the shell the server opens for it. */
  registerView(viewId: string, view: vscode.WebviewView): void {
    this.views.set(viewId, view);
    view.description = this.viewDescriptions.get(viewId) || undefined;
    view.webview.options = { enableScripts: true, localResourceRoots: this.roots() };
    const shell = this.viewShells.get(viewId);
    if (shell) {
      this.attachView(shell, viewId);
    } else {
      view.webview.html = this.emptyHtml();
    }
    view.onDidDispose(() => {
      this.views.delete(viewId);
      if (viewFocus.view === viewId) {
        this.setViewFocus('');
      }
      const s = this.viewShells.get(viewId);
      if (s) {
        const h = this.hosts.get(s);
        if (h) {
          h.ready = false;
        }
      }
    });
  }

  private emptyHtml(): string {
    return '<!DOCTYPE html><html><body></body></html>';
  }

  private html(webview: vscode.Webview): string {
    const n = nonce();
    const js = webview.asWebviewUri(vscode.Uri.joinPath(this.context.extensionUri, 'media', 'rswt', 'renderer.js'));
    const css = webview.asWebviewUri(vscode.Uri.joinPath(this.context.extensionUri, 'media', 'rswt', 'renderer.css'));
    return `<!DOCTYPE html><html><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${webview.cspSource} data:; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${n}'; frame-src ${webview.cspSource} http://127.0.0.1:* http://localhost:* https: data: about:;">
<link rel="stylesheet" href="${css}"></head><body><div id="root"></div><script nonce="${n}" src="${js}"></script></body></html>`;
  }

  private wire(h: ShellHost): void {
    h.webview.onDidReceiveMessage(async (m: { type: string; id?: string; eventType?: string; data?: unknown; title?: string; text?: string; url?: string }) => {
      if (m.type === 'ready') {
        h.ready = false;
        try {
          const snap = await this.client.request<{ closed?: boolean; seq: number; ops: Op[]; title: string }>('ui/snapshot', { shell: h.shell });
          if (snap.closed) {
            h.closingFromServer = true;
            h.dispose();
            return;
          }
          h.snapshotSeq = snap.seq;
          await this.post(h, 'snapshot', snap.ops);
          h.ready = true;
          for (const b of h.buffered.splice(0)) {
            if (b.seq > h.snapshotSeq) {
              await this.post(h, 'update', b.ops);
            }
          }
          if (h.panel && snap.title) {
            h.panel.title = snap.title;
          }
        } catch (e) {
          this.client.log.appendLine(`[rswt] snapshot failed: ${e}`);
        }
      } else if (m.type === 'event' && m.id && m.eventType) {
        this.client.notify('ui/event', { id: m.id, type: m.eventType, data: m.data ?? {} });
      } else if (m.type === 'title' && h.panel && typeof m.title === 'string' && m.title) {
        h.panel.title = m.title;
      } else if (m.type === 'focus') {
        this.client.notify('ui/shellFocused', { shell: h.shell });
        if (h.view) {
          this.setViewFocus(h.view);
        }
      } else if (m.type === 'blur') {
        if (h.view && viewFocus.view === h.view) {
          this.setViewFocus('');
        }
      } else if (m.type === 'alert' || m.type === 'open-external') {
        handleBridgeMessage(m as BridgeMessage);
      } else if (m.type === 'reveal') {
        h.panel?.reveal();
      }
    });
  }

  /** Moves a shell panel to the editor group right of another one (Eclipse EModelService.insert RIGHT_OF). */
  private place(shell: string, beside?: string): void {
    const h = this.hosts.get(shell);
    if (!h?.panel) {
      return;
    }
    const left = beside ? this.hosts.get(beside)?.panel : undefined;
    const column = (left?.viewColumn ?? vscode.ViewColumn.One) + 1;
    if (h.panel.viewColumn !== column) {
      h.panel.reveal(column, true);
    }
    left?.reveal(left.viewColumn, true);
    h.panel.reveal(column, false);
  }

  private open(p: OpenShell): void {
    const existing = this.hosts.get(p.shell);
    if (existing) {
      existing.panel?.reveal();
      return;
    }
    if (p.host === 'view' && p.view) {
      this.viewShells.set(p.view, p.shell);
      if (this.views.has(p.view)) {
        this.attachView(p.shell, p.view);
      }
      return;
    }
    const panel = vscode.window.createWebviewPanel('requality.dialog', p.title || 'Requality', vscode.ViewColumn.Active, {
      enableScripts: true,
      retainContextWhenHidden: true,
      localResourceRoots: this.roots(),
    });
    panel.iconPath = vscode.Uri.joinPath(this.context.extensionUri, 'media', 'requality.svg');
    const h: ShellHost = {
      shell: p.shell,
      webview: panel.webview,
      ready: false,
      snapshotSeq: 0,
      buffered: [],
      queue: Promise.resolve(),
      panel,
      dispose: () => panel.dispose(),
    };
    this.hosts.set(p.shell, h);
    this.wire(h);
    // the editor group of the tab: the E4 model of the editors on the server follows the VS Code groups
    let column = panel.viewColumn;
    if (column !== undefined) {
      this.client.notify('ui/shellColumn', { shell: p.shell, column });
    }
    panel.onDidChangeViewState(e => {
      if (e.webviewPanel.viewColumn !== column && e.webviewPanel.viewColumn !== undefined) {
        column = e.webviewPanel.viewColumn;
        this.client.notify('ui/shellColumn', { shell: p.shell, column });
      }
      if (e.webviewPanel.active) {
        this.client.notify('ui/shellFocused', { shell: p.shell });
      }
    });
    panel.onDidDispose(() => {
      this.hosts.delete(p.shell);
      if (!h.closingFromServer) {
        // The user closed the tab: the shell handles it like a window close.
        this.client.notify('ui/event', { id: p.shell, type: 'close', data: {} });
      }
    });
    panel.webview.html = this.html(panel.webview);
  }

  private attachView(shell: string, viewId: string): void {
    const view = this.views.get(viewId)!;
    const h: ShellHost = {
      shell,
      webview: view.webview,
      ready: false,
      snapshotSeq: 0,
      buffered: [],
      queue: Promise.resolve(),
      view: viewId,
      dispose: () => {
        view.webview.html = this.emptyHtml();
      },
    };
    this.hosts.set(shell, h);
    this.wire(h);
    view.webview.html = this.html(view.webview);
  }

  private close(shell: string): void {
    const h = this.hosts.get(shell);
    for (const [view, s] of this.viewShells) {
      if (s === shell) {
        this.viewShells.delete(view);
      }
    }
    if (!h) {
      return;
    }
    this.hosts.delete(shell);
    h.closingFromServer = true;
    h.dispose();
  }

  private update(shell: string, seq: number, ops: Op[]): void {
    const h = this.hosts.get(shell);
    if (!h) {
      return;
    }
    if (!h.ready) {
      h.buffered.push({ seq, ops });
      return;
    }
    if (seq <= h.snapshotSeq) {
      return;
    }
    void this.post(h, 'update', ops);
  }

  /**
   * Pages of the Requality resources server (Browser widgets) are addressed through asExternalUri, so they also load
   * when VS Code runs remotely.
   */
  private async mapUrls(ops: Op[]): Promise<Op[]> {
    const out: Op[] = [];
    for (const op of ops) {
      const url = op.props?.url;
      if (typeof url === 'string' && /^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?\//.test(url)) {
        const external = await vscode.env.asExternalUri(vscode.Uri.parse(url));
        out.push({ ...op, props: { ...op.props, url: external.toString(true) } });
      } else {
        out.push(op);
      }
    }
    return out;
  }

  /** Posts operations to a webview in order (after URL mapping). */
  private post(h: ShellHost, type: 'snapshot' | 'update', ops: Op[]): Promise<void> {
    h.queue = h.queue.then(async () => {
      const mapped = await this.mapUrls(ops);
      await h.webview.postMessage({ type, ops: mapped, images: this.images(h.webview, mapped) });
    }).catch(e => this.client.log.appendLine(`[rswt] ${e}`));
    return h.queue;
  }

  /** Maps the image files referenced by the operations to URLs of the webview. */
  private images(webview: vscode.Webview, ops: Op[]): Record<string, string> {
    const out: Record<string, string> = {};
    const roots = this.roots().map(r => r.fsPath);
    const add = (p: unknown) => {
      if (typeof p === 'string' && p && !(p in out) && path.isAbsolute(p)) {
        out[p] = imageUrl(p, roots, f => webview.asWebviewUri(vscode.Uri.file(f)).toString());
      }
    };
    for (const op of ops) {
      const props = op.props;
      if (!props) {
        continue;
      }
      add(props.image);
      if (Array.isArray(props.images)) {
        props.images.forEach(add);
      }
    }
    return out;
  }

  dispose(): void {
    for (const s of this.subs) {
      s.dispose();
    }
    for (const h of [...this.hosts.values()]) {
      h.closingFromServer = true;
      h.dispose();
    }
  }
}
