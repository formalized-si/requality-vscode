import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { App, command } from '../app';
import { ExplorerItem } from '../views/explorer';
import { helpPageHtml } from './helpPage';
import { ECLIPSE_EXPLORER_ID, vscodeViewId } from './serverCommands';
import { ECLIPSE_EXPLORER_VIEW, NATIVE_EXPLORER_VIEW } from '../views/explorerMode';

interface Problem {
  message: string;
  severity: number;
  line: number;
  location: string;
  type: string;
}

/** IMarker.SEVERITY_ERROR / WARNING / INFO. */
function severity(s: number): vscode.DiagnosticSeverity {
  return s >= 2 ? vscode.DiagnosticSeverity.Error : s === 1 ? vscode.DiagnosticSeverity.Warning : vscode.DiagnosticSeverity.Information;
}

interface TocTopic {
  label: string;
  href?: string;
  topics: TocTopic[];
}

/** Eclipse help table of contents (org.eclipse.help.toc docs/manual/toc.xml). */
function parseToc(xml: string): TocTopic[] {
  const root: TocTopic = { label: '', topics: [] };
  const stack: TocTopic[] = [root];
  const re = /<(\/?)topic\b([^>]*?)(\/?)>/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(xml))) {
    if (m[1]) {
      stack.pop();
      continue;
    }
    const attr = (name: string) => {
      const a = new RegExp(`${name}="([^"]*)"`).exec(m![2]);
      return a ? a[1] : undefined;
    };
    const t: TocTopic = { label: attr('label') ?? '', href: attr('href'), topics: [] };
    stack[stack.length - 1].topics.push(t);
    if (!m[3]) {
      stack.push(t);
    }
  }
  return root.topics;
}

/** The page the manual shows (its title), reported by the webview after a page is loaded. */
export const helpState = { title: '' };

/** The window title the server computes (client/windowTitle) and the window.title template made of it. */
export const windowState = { title: '', template: '' };

/** The perspectives whose layout was applied, in order (exported for the tests). */
export const layoutState = { applied: [] as string[] };

/** The VS Code containers of the regions of a perspective's layout. */
const REGION_CONTAINERS: Record<string, string> = {
  left: 'workbench.view.extension.requality',
  right: 'workbench.view.extension.requality-right',
  top: 'workbench.view.extension.requality-panel',
  bottom: 'workbench.view.extension.requality-panel',
};

/**
 * Whether VS Code has the secondary side bar for the views of extensions (1.106 and newer). Before, VS Code puts the
 * views of that container in its Explorer, and the views right of the editor area go to the primary side bar.
 */
export function hasSecondarySidebar(version = vscode.version): boolean {
  const [major, minor] = version.split('.').map(n => parseInt(n, 10));
  return major > 1 || (major === 1 && minor >= 106);
}

/** The VS Code container of a region of a perspective's layout. */
export function regionContainer(region: string, version = vscode.version): string | undefined {
  return region === 'right' && !hasSecondarySidebar(version) ? REGION_CONTAINERS.left : REGION_CONTAINERS[region];
}

/** Moves the views of a perspective's layout to the containers of their regions (VS Code ignores a missing container). */
async function applyViewLayout(views: Array<{ id: string; region: string }>): Promise<void> {
  const byContainer = new Map<string, string[]>();
  for (const v of views) {
    const container = regionContainer(v.region);
    // the Requality Explorer: its two views (the Eclipse view and the native tree) take the place of the part
    const ids = v.id === ECLIPSE_EXPLORER_ID ? [ECLIPSE_EXPLORER_VIEW, NATIVE_EXPLORER_VIEW] : [vscodeViewId(v.id)];
    if (container && ids[0].startsWith('requality.')) {
      byContainer.set(container, [...(byContainer.get(container) ?? []), ...ids]);
    }
  }
  for (const [destinationId, viewIds] of byContainer) {
    try {
      await vscode.commands.executeCommand('vscode.moveViews', { viewIds, destinationId });
    } catch {
      // a VS Code without the container keeps the views where they are
    }
  }
}

/** The window.title of the parts of the Eclipse title, the active editor of VS Code in place of Eclipse's. */
export function windowTitleTemplate(before: string[], editor: boolean, after: string[]): string {
  const literal = (t: string) => t.replace(/\$\{/g, '$ {');
  const parts = [...before.map(literal), ...(editor ? ['${activeEditorMedium}'] : []), ...after.map(literal)];
  return parts.length === 0 ? '' : '${dirty}' + parts.join('${separator}');
}

const TITLE_KEY = 'requality.windowTitle.written';

/** Sets window.title of the workspace to the Eclipse title, unless the user set another title there. */
async function applyWindowTitle(context: vscode.ExtensionContext, template: string): Promise<void> {
  if (!template || !vscode.workspace.workspaceFolders?.length || !vscode.workspace.getConfiguration('requality').get('window.title', true)) {
    return;
  }
  const config = vscode.workspace.getConfiguration('window');
  const current = config.inspect<string>('title')?.workspaceValue;
  const written = context.workspaceState.get<string>(TITLE_KEY);
  if (current !== undefined && current !== written) {
    return; // the user's own title
  }
  if (current !== template) {
    await config.update('title', template, vscode.ConfigurationTarget.Workspace);
  }
  await context.workspaceState.update(TITLE_KEY, template);
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/**
 * Workbench services of the extension: the Problems panel for problem markers, Preferences and project Properties
 * (preference and property pages of plugin.xml), and the Requality manual (Eclipse help contents).
 */
export function activateWorkbench(app: App): void {
  const { client, context } = app;
  const subs = context.subscriptions;

  // ---- problem markers -> diagnostics
  const diagnostics = vscode.languages.createDiagnosticCollection('requality');
  subs.push(diagnostics);
  subs.push(client.onNotification('problems/set', (p: { files: Array<{ uri: string; problems: Problem[] }> }) => {
    for (const f of p.files) {
      const uri = vscode.Uri.parse(f.uri);
      diagnostics.set(uri, f.problems.map(pr => {
        const line = pr.line > 0 ? pr.line - 1 : 0;
        const d = new vscode.Diagnostic(new vscode.Range(line, 0, line, 0), pr.message, severity(pr.severity));
        d.source = 'Requality';
        return d;
      }));
    }
  }));
  subs.push(client.onDidChangeState(s => {
    if (s !== 'ready') {
      diagnostics.clear();
    }
  }));

  // ---- Window > Perspective. The layout of a perspective (client/viewLayout: the region of each of its views) is
  // applied when the perspective is opened the first time and when it is reset (Reset Perspective resets the view
  // locations of VS Code first): left of the editor area is the primary side bar, right the secondary side bar (VS Code
  // versions that have one for extensions), top and bottom the panel.
  let resetting: Thenable<unknown> = Promise.resolve();
  subs.push(client.onNotification('client/resetViewLocations', () => {
    resetting = vscode.commands.executeCommand('workbench.action.resetViewLocations');
  }));
  subs.push(client.onNotification('client/viewLayout', async (p: { perspective: string; reset: boolean; views: Array<{ id: string; region: string }> }) => {
    const key = `requality.layout.${p.perspective}`;
    if (!p.reset && context.globalState.get(key)) {
      return;
    }
    await resetting;
    await applyViewLayout(p.views);
    layoutState.applied.push(p.perspective);
    await context.globalState.update(key, true);
  }));
  // The window title of IDEWorkbenchWindowAdvisor.computeTitle (workspace name, perspective, active editor, location,
  // product, as Preferences > General > Workspace selects them) is the title of the VS Code window (its window.title
  // setting of the workspace, unless the user set another one), with the active editor of VS Code; the current
  // perspective is in the status bar (the perspective switcher of Eclipse: a click opens Open Perspective).
  const perspectiveItem = vscode.window.createStatusBarItem('requality.perspective', vscode.StatusBarAlignment.Left, -100);
  perspectiveItem.name = 'Requality Perspective';
  perspectiveItem.command = 'requality.perspective.openOther';
  subs.push(perspectiveItem);
  subs.push(client.onNotification('client/windowTitle', (p: { title?: string; before?: string[]; editor?: boolean; after?: string[]; perspective?: string | null }) => {
    windowState.title = p.title ?? '';
    windowState.template = windowTitleTemplate(p.before ?? [], !!p.editor, p.after ?? []);
    void applyWindowTitle(context, windowState.template);
    if (p.perspective) {
      perspectiveItem.text = `$(window) ${p.perspective}`;
      perspectiveItem.tooltip = `${p.perspective}\nOpen Perspective...`;
      perspectiveItem.show();
    } else {
      perspectiveItem.hide();
    }
  }));
  subs.push(client.onDidChangeState(s => {
    if (s !== 'ready') {
      perspectiveItem.hide();
    }
  }));

  // ---- Properties view: Pin to Selection (PinPropertySheetAction)
  for (const [id, pinned] of [['requality.properties.pin', true], ['requality.properties.unpin', false]] as const) {
    subs.push(command(app, id, async () => {
      await client.request('properties/pin', { pinned });
      void vscode.commands.executeCommand('setContext', 'requality.properties.pinned', pinned);
    }));
  }

  // ---- Preferences / project Properties
  subs.push(command(app, 'requality.preferences', () => client.request('preferences/open', {})));
  subs.push(command(app, 'requality.projectProperties', async (item?: ExplorerItem) => {
    const target = item && typeof item === 'object' && 'id' in item ? item.id : app.selection.value.items?.[0];
    if (!target) {
      void vscode.window.showInformationMessage('Select a Requality project first.');
      return;
    }
    await client.request('properties/openProject', { project: target });
  }));

  // ---- help (Eclipse help system with the plug-in's table of contents)
  let helpPanel: vscode.WebviewPanel | undefined;
  const HELP_BOOKMARKS = 'requality.help.bookmarks';
  const bookmarks = (): Array<{ href: string; title: string }> => context.globalState.get(HELP_BOOKMARKS, []);
  const postBookmarks = () => void helpPanel?.webview.postMessage({ type: 'bookmarks', list: bookmarks() });
  const resources = vscode.Uri.joinPath(context.extensionUri, 'dist', 'server', 'resources');
  const showHelp = (href?: string | null) => {
    const ru = vscode.env.language.startsWith('ru');
    const tocFile = path.join(resources.fsPath, 'docs', 'manual', ru ? 'toc.xml' : 'toc.en.xml');
    const topics = fs.existsSync(tocFile) ? parseToc(fs.readFileSync(tocFile, 'utf8')) : [];
    if (!helpPanel) {
      helpPanel = vscode.window.createWebviewPanel('requality.help', 'Requality Manual', vscode.ViewColumn.Active, {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [resources],
      });
      helpPanel.iconPath = vscode.Uri.joinPath(context.extensionUri, 'media', 'requality.svg');
      helpPanel.onDidDispose(() => (helpPanel = undefined));
      helpPanel.webview.onDidReceiveMessage((m: { type?: string; url?: string; title?: string; href?: string }) => {
        if (m.type === 'external' && typeof m.url === 'string' && /^(https?|mailto):/i.test(m.url)) {
          void vscode.env.openExternal(vscode.Uri.parse(m.url));
        } else if (m.type === 'loaded') {
          helpState.title = m.title ?? '';
        } else if (m.type === 'bookmark' && typeof m.href === 'string') {
          // Bookmark Document: the bookmarks of the help are kept across sessions (the Eclipse help keeps them too)
          const list = bookmarks().filter(b => b.href !== m.href);
          list.push({ href: m.href, title: m.title || m.href });
          void context.globalState.update(HELP_BOOKMARKS, list).then(() => postBookmarks());
        } else if (m.type === 'removeBookmark') {
          const list = typeof m.href === 'string' ? bookmarks().filter(b => b.href !== m.href) : [];
          void context.globalState.update(HELP_BOOKMARKS, list).then(() => postBookmarks());
        }
      });
    }
    const webview = helpPanel.webview;
    const root = webview.asWebviewUri(resources).toString().replace(/\/?$/, '/');
    const toUri = (h: string) => {
      const clean = h.replace(/^\/?com\.unitesk\.requality\//, '').replace(/^\//, '');
      const [file, anchor] = clean.split('#');
      return webview.asWebviewUri(vscode.Uri.joinPath(resources, file)).toString() + (anchor ? '#' + anchor : '');
    };
    const render = (ts: TocTopic[]): string => ts.length === 0 ? '' : '<ul>' + ts.map(t =>
      `<li>${t.href ? `<a href="#" data-src="${escapeHtml(toUri(t.href))}">${escapeHtml(t.label)}</a>` : escapeHtml(t.label)}${render(t.topics)}</li>`).join('') + '</ul>';
    const first = href ? toUri(href) : topics[0]?.href ? toUri(topics[0].href) : toUri('docs/manual/index.html');
    const home = topics[0]?.href ? toUri(topics[0].href) : toUri('docs/manual/index.html');
    const nonce = Math.random().toString(36).slice(2);
    // The pages are fetched by the webview (its resource requests are served by VS Code) and shown in a srcdoc frame
    // with a <base> of their folder: a nested frame navigating to a resource URL would not be served. Links between
    // pages are followed the same way; external links open in the browser. The Eclipse help window around them: the
    // Contents, Search Results and Bookmarks tabs, the search field, and Go Back, Go Forward, Home, Show in Table of
    // Contents, Bookmark Document and Print Page.
    webview.html = helpPageHtml({ cspSource: webview.cspSource, nonce, toc: render(topics), root, home, first, bookmarks: bookmarks() });
    helpPanel.reveal();
  };
  subs.push(command(app, 'requality.help', () => showHelp()));

  // ---- Eclipse Compare: the content merge viewer of plugin.xml for two versions of a node file
  const side = async (u: vscode.Uri) => {
    const name = path.posix.basename(u.path);
    let label = vscode.workspace.asRelativePath(u.scheme === 'file' ? u : u.with({ scheme: 'file', query: '' }));
    if (u.scheme === 'file') {
      return { name, path: u.fsPath, label };
    }
    try {
      const ref = (JSON.parse(u.query) as { ref?: string }).ref;
      if (ref !== undefined) {
        label += ` (${ref === '' ? 'Index' : ref === '~' ? 'HEAD' : ref})`;
      }
    } catch {
      // not a git URI
    }
    const doc = await vscode.workspace.openTextDocument(u);
    return { name, path: u.fsPath, label, content: doc.getText() };
  };
  subs.push(command(app, 'requality.compare', async (_uri?: vscode.Uri, uris?: vscode.Uri[]) => {
    let left: vscode.Uri | undefined, right: vscode.Uri | undefined;
    if (Array.isArray(uris) && uris.length === 2) {
      [left, right] = uris;
    } else {
      const input = vscode.window.tabGroups.activeTabGroup.activeTab?.input;
      if (input instanceof vscode.TabInputTextDiff) {
        left = input.original;
        right = input.modified;
      }
    }
    if (!left || !right) {
      void vscode.window.showInformationMessage('Select two Requality node files (.json) or open their diff first.');
      return;
    }
    const [l, r] = await Promise.all([side(left), side(right)]);
    const ok = await client.request<boolean>('compare/open', { title: `Compare ('${l.label}' - '${r.label}')`, left: l, right: r });
    if (!ok) {
      void vscode.window.showInformationMessage('No Requality compare viewer for this file type.');
    }
  }));
  subs.push(client.onNotification('client/showHelp', (p: { href?: string | null }) => showHelp(p.href)));
}
