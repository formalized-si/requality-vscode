import * as vscode from 'vscode';
import { RequalityClient } from '../server/connection';

/** An item of the Requality Explorer (ExplorerService.item / projectItem). */
export interface ExplorerItem {
  id: string;
  kind: 'project' | 'node' | 'resource';
  label: string;
  icon: string;
  hasChildren: boolean;
  contextValue: string;
  /** Node type (nodes only). */
  type?: string;
  /** Qualified id (nodes only). */
  qid?: string;
  /** Root node id (loaded projects only). */
  node?: string;
  /** Folder of a project, or the file or folder of a resource. */
  path?: string;
  /** Description of the element (the status line text of Eclipse), shown as tool tip. */
  tooltip?: string;
  /** Colors and font set by the lightweight decorators (IDecoration). */
  decoration?: { foreground?: string; background?: string; italic?: boolean; bold?: boolean };
}

/**
 * Colors of the decorators (LinksDecorator, SelectedRefTargetDecorator, RepoDecorator) as theme colors contributed by
 * the extension (package.json "colors"). VS Code trees cannot paint item backgrounds, so a decorator background
 * colors the label.
 */
const DECORATION_COLORS: Record<string, string> = {
  '#ff81a3': 'requality.decoration.linkedFrom',
  '#abddfa': 'requality.decoration.linkedTo',
  '#808080': 'requality.decoration.referenceTarget',
  '#646464': 'requality.decoration.repository',
};

const DECORATION_SCHEME = 'requality-node';

/** Label colors of decorated explorer items. */
class ExplorerDecorations implements vscode.FileDecorationProvider {
  private readonly emitter = new vscode.EventEmitter<vscode.Uri[]>();
  readonly onDidChangeFileDecorations = this.emitter.event;
  private readonly colors = new Map<string, string>();

  uriOf(item: ExplorerItem): vscode.Uri | undefined {
    const d = item.decoration;
    const color = d && (DECORATION_COLORS[(d.background ?? '').toLowerCase()] ?? DECORATION_COLORS[(d.foreground ?? '').toLowerCase()]);
    const uri = vscode.Uri.from({ scheme: DECORATION_SCHEME, path: '/' + encodeURIComponent(item.id) });
    const old = this.colors.get(uri.toString());
    if (color) {
      this.colors.set(uri.toString(), color);
    } else {
      this.colors.delete(uri.toString());
    }
    if (old !== color) {
      this.emitter.fire([uri]);
    }
    return color ? uri : undefined;
  }

  provideFileDecoration(uri: vscode.Uri): vscode.FileDecoration | undefined {
    if (uri.scheme !== DECORATION_SCHEME) {
      return undefined;
    }
    const color = this.colors.get(uri.toString());
    return color ? new vscode.FileDecoration(undefined, undefined, new vscode.ThemeColor(color)) : undefined;
  }
}

/** The frames of the explorer (ExplorerService.frames). */
interface FrameState {
  back: boolean;
  forward: boolean;
  up: boolean;
  backToolTip: string;
  forwardToolTip: string;
  upToolTip: string;
  titleToolTip: string;
}

const DOUBLE_CLICK_MS = 450;

/** Mime type of the explorer items being dragged (the local selection transfer of the Common Navigator). */
const TREE_MIME = 'application/vnd.code.tree.requality.explorer';

/** The node an item stands for: the root node for a project item. */
export function nodeIdOf(item: ExplorerItem | undefined): string | undefined {
  if (!item || item.kind === 'resource') {
    return undefined;
  }
  return item.kind === 'project' ? item.node : item.id;
}

/**
 * Requality Explorer (Eclipse RequalityCNF): projects of the workspace and their node trees. Single click selects
 * (and updates Properties), double click or Enter opens, as in the Eclipse Common Navigator.
 */
export class RequalityExplorer implements vscode.TreeDataProvider<ExplorerItem>, vscode.TreeDragAndDropController<ExplorerItem>, vscode.Disposable {
  // the file URIs of dragged projects, folders and files go along (ResourceTransfer of Eclipse): server views and
  // dialogs (the History view, Compare with Other Resource) take them as dropped resources
  readonly dragMimeTypes = [TREE_MIME, 'text/uri-list'];
  readonly dropMimeTypes = [TREE_MIME, 'text/uri-list'];
  private readonly changeEmitter = new vscode.EventEmitter<ExplorerItem | undefined | void>();
  readonly onDidChangeTreeData = this.changeEmitter.event;
  private readonly selectionEmitter = new vscode.EventEmitter<ExplorerItem[]>();
  /** Fires with the selected items (also when an already selected item is clicked again). */
  readonly onDidSelect = this.selectionEmitter.event;
  private readonly titleEmitter = new vscode.EventEmitter<string | undefined>();
  /** Fires with the title tool tip of the explorer part (the input the explorer went into, if any). */
  readonly onDidChangeTitleToolTip = this.titleEmitter.event;
  /** Last returned instance per id; VS Code identifies tree elements by object identity. */
  private readonly items = new Map<string, ExplorerItem>();
  private readonly parents = new Map<string, string | undefined>();
  readonly view: vscode.TreeView<ExplorerItem>;
  private readonly subs: vscode.Disposable[] = [];
  private lastClick: { id: string; time: number } | undefined;
  private serverSelection: string | undefined;
  private readonly decorations = new ExplorerDecorations();

  constructor(private readonly client: RequalityClient) {
    this.view = vscode.window.createTreeView('requality.explorer', {
      treeDataProvider: this,
      canSelectMany: true,
      showCollapseAll: true,
      dragAndDropController: this,
    });
    this.subs.push(this.view);
    this.subs.push(vscode.window.registerFileDecorationProvider(this.decorations));
    this.subs.push(this.view.onDidChangeSelection(e => this.selectionEmitter.fire([...e.selection])));
    this.subs.push(client.onNotification('explorer/refresh', (p: { ids: string[] | null }) => void this.refresh(p.ids)));
    this.subs.push(client.onNotification('workspace/projectsChanged', () => this.changeEmitter.fire()));
    this.subs.push(client.onNotification('explorer/reveal', (p: { node: string; select: boolean; expand: boolean; focus: boolean }) => {
      if (p.select) {
        // RequalityCNF.selectReveal disables the tree listener: a selection made by the server is not sent back.
        this.serverSelection = p.node;
      }
      void this.reveal(p.node, { select: p.select, expand: p.expand, focus: p.focus }).catch(() => undefined);
    }));
    this.subs.push(client.onDidChangeState(s => {
      if (s === 'ready' || s === 'failed' || s === 'stopped') {
        this.items.clear();
        this.parents.clear();
        this.changeEmitter.fire();
      }
      if (s === 'ready') {
        void client.request<FrameState>('explorer/frames').then(f => this.applyFrames(f), () => undefined);
      } else {
        this.applyFrames(undefined);
      }
    }));
    this.subs.push(vscode.commands.registerCommand('requality.explorer.click', (id: string) => this.click(id)));
    // Frames (Go Into, Back, Forward, Up): the input of the viewer is the parent of the roots of this tree.
    this.subs.push(client.onNotification('explorer/frames', (f: FrameState) => this.applyFrames(f)));
    for (const action of ['back', 'forward', 'up']) {
      this.subs.push(vscode.commands.registerCommand(`requality.explorer.${action}`, async () => {
        this.applyFrames(await client.request<FrameState>('explorer/frameAction', { action }));
      }));
    }
    // CommonViewer.getExpandedElements: a frame keeps the expanded items to restore them.
    this.subs.push(this.view.onDidExpandElement(e => client.notify('explorer/expanded', { id: e.element.id, expanded: true })));
    this.subs.push(this.view.onDidCollapseElement(e => client.notify('explorer/expanded', { id: e.element.id, expanded: false })));
  }

  /**
   * The frame actions of the view toolbar are shown while one of them is enabled (CommonNavigatorActionGroup). The
   * title tool tip of the Eclipse view (the input the explorer went into) is the description of the VS Code view,
   * which has no title tool tip.
   */
  private applyFrames(f: FrameState | undefined): void {
    const shown = !!f && (f.back || f.forward || f.up);
    void vscode.commands.executeCommand('setContext', 'requality.explorer.frames', shown);
    void vscode.commands.executeCommand('setContext', 'requality.explorer.canBack', !!f?.back);
    void vscode.commands.executeCommand('setContext', 'requality.explorer.canForward', !!f?.forward);
    void vscode.commands.executeCommand('setContext', 'requality.explorer.canUp', !!f?.up);
    this.view.description = f?.titleToolTip || undefined;
    this.titleEmitter.fire(f?.titleToolTip || undefined);
    // the roots changed: the parents known for the items no longer hold
    this.parents.clear();
  }

  /** True (once) when the selection change was made by the server (explorer/reveal with select). */
  consumeServerSelection(items: readonly ExplorerItem[]): boolean {
    const s = this.serverSelection;
    if (s && items.length === 1 && items[0].id === s) {
      this.serverSelection = undefined;
      return true;
    }
    return false;
  }

  get selection(): readonly ExplorerItem[] {
    return this.view.selection;
  }

  /** Selected node ids (project items count as their root node). */
  selectedNodeIds(): string[] {
    return this.view.selection.map(nodeIdOf).filter((x): x is string => !!x);
  }

  item(id: string): ExplorerItem | undefined {
    return this.items.get(id);
  }

  private remember(list: ExplorerItem[], parent: string | undefined): ExplorerItem[] {
    return list.map(fresh => {
      const old = this.items.get(fresh.id);
      const item = old ? Object.assign(old, fresh) : fresh;
      this.items.set(item.id, item);
      this.parents.set(item.id, parent);
      return item;
    });
  }

  async getChildren(element?: ExplorerItem): Promise<ExplorerItem[]> {
    if (this.client.state !== 'ready') {
      return [];
    }
    try {
      if (!element) {
        return this.remember(await this.client.request<ExplorerItem[]>('explorer/roots'), undefined);
      }
      return this.remember(await this.client.request<ExplorerItem[]>('explorer/children', { id: element.id }), element.id);
    } catch (e) {
      this.client.log.appendLine(`[explorer] ${e}`);
      return [];
    }
  }

  getTreeItem(element: ExplorerItem): vscode.TreeItem {
    const item = new vscode.TreeItem(element.label,
      element.hasChildren ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None);
    item.id = element.id;
    item.contextValue = element.contextValue;
    // IDE images of projects: prj_obj (open) and cprj_obj (closed) sent as image paths; files and folders get the
    // images of the file icon theme (as the editor registry gives Eclipse its file images), and a resource decorated
    // by the IDE decorators (linked, symbolic link, virtual, resource filters) the workbench image with their overlays.
    if (element.kind === 'resource' && element.path) {
      item.resourceUri = vscode.Uri.file(element.path);
      item.iconPath = element.icon === 'folder' ? vscode.ThemeIcon.Folder
        : element.icon === 'file' || !element.icon ? vscode.ThemeIcon.File : vscode.Uri.file(element.icon);
    } else {
      item.iconPath = element.icon === 'project' ? new vscode.ThemeIcon('project')
        : element.icon === 'closedProject' ? new vscode.ThemeIcon('folder') : vscode.Uri.file(element.icon);
    }
    item.command = { command: 'requality.explorer.click', title: '', arguments: [element.id] };
    if (element.kind === 'project' && element.path) {
      item.tooltip = element.path;
    } else if (element.tooltip) {
      item.tooltip = element.tooltip;
    }
    const uri = element.kind === 'resource' ? undefined : this.decorations.uriOf(element);
    if (uri) {
      item.resourceUri = uri;
    }
    if (element.decoration?.bold || element.decoration?.italic) {
      item.label = { label: element.label, highlights: [[0, element.label.length]] };
    }
    return item;
  }

  async getParent(element: ExplorerItem): Promise<ExplorerItem | undefined> {
    let parentId = this.parents.get(element.id);
    if (parentId === undefined) {
      parentId = (await this.client.request<string | null>('explorer/parent', { id: element.id })) ?? undefined;
    }
    if (!parentId) {
      return undefined;
    }
    return this.items.get(parentId) ?? (await this.fetch(parentId));
  }

  private async fetch(id: string): Promise<ExplorerItem | undefined> {
    const fresh = await this.client.request<ExplorerItem | null>('explorer/item', { id });
    if (!fresh) {
      return undefined;
    }
    const old = this.items.get(id);
    const item = old ? Object.assign(old, fresh) : fresh;
    this.items.set(id, item);
    return item;
  }

  /** Refreshes the given items (labels, icons and children); null refreshes everything. */
  async refresh(ids?: string[] | null): Promise<void> {
    if (!ids) {
      this.changeEmitter.fire();
      return;
    }
    for (const id of ids) {
      const known = this.items.get(id);
      if (!known) {
        continue;
      }
      try {
        const fresh = await this.client.request<ExplorerItem | null>('explorer/item', { id });
        if (!fresh) {
          const parent = this.parents.get(id);
          this.items.delete(id);
          this.changeEmitter.fire(parent ? this.items.get(parent) : undefined);
          continue;
        }
        Object.assign(known, fresh);
        this.changeEmitter.fire(known);
      } catch {
        this.changeEmitter.fire();
      }
    }
  }

  /** Reveals and selects a node, loading its ancestors. */
  async reveal(nodeId: string, options: { focus?: boolean; select?: boolean; expand?: boolean } = {}): Promise<void> {
    let item = this.items.get(nodeId) ?? (await this.fetch(nodeId));
    if (!item) {
      return;
    }
    await this.view.reveal(item, { select: options.select ?? true, focus: options.focus ?? false, expand: options.expand ?? false });
  }

  handleDrag(source: readonly ExplorerItem[], dataTransfer: vscode.DataTransfer): void {
    dataTransfer.set(TREE_MIME, new vscode.DataTransferItem(source.map(s => s.id)));
    const files = source.filter(s => (s.kind === 'resource' || s.kind === 'project') && s.path)
      .map(s => vscode.Uri.file(s.path as string).toString());
    if (files.length) {
      dataTransfer.set('text/uri-list', new vscode.DataTransferItem(files.join('\r\n')));
    }
  }

  /**
   * CommonDropAdapter: nodes dropped on a node go to the drop assistant of the explorer (CNFDropAssistant); Eclipse
   * reads Shift (insert after the target) and Ctrl (reuse: RequalityCNF.altPressed is set by the Ctrl key) from the
   * keyboard, which a VS Code drop does not report, so the user picks the kind of drop. Files dropped on a project or folder are copied into it.
   */
  async handleDrop(target: ExplorerItem | undefined, dataTransfer: vscode.DataTransfer): Promise<void> {
    if (!target || this.client.state !== 'ready') {
      return;
    }
    const nodes = dataTransfer.get(TREE_MIME);
    if (nodes) {
      const dragged = (nodes.value as string[]).filter(id => id !== target.id);
      // ResourceDropAdapterAssistant: projects, folders and files dropped on a project, folder or file are moved
      // into it (copied with Ctrl in Eclipse; VS Code reports no modifier keys, so the user picks).
      const resources = dragged.filter(id => ['project', 'resource'].includes(this.items.get(id)?.kind ?? ''));
      if (resources.length && (target.kind === 'project' || target.kind === 'resource')) {
        const name = target.label;
        const pick = await vscode.window.showQuickPick([
          { label: `Move to '${name}'`, operation: 'move' },
          { label: `Copy to '${name}'`, description: 'Ctrl', operation: 'copy' },
        ], { title: 'Drop', placeHolder: `Drop ${resources.length === 1 ? this.items.get(resources[0])?.label : resources.length + ' resources'}` });
        if (pick) {
          const r = await this.client.request<boolean | string>('explorer/drop', { sources: resources, target: target.id, operation: pick.operation });
          if (typeof r === 'string') {
            void vscode.window.showWarningMessage(r);
          }
          this.changeEmitter.fire();
        }
        return;
      }
      const sources = dragged.filter(id => this.items.get(id)?.kind === 'node');
      if (!sources.length || target.kind !== 'node') {
        return;
      }
      const name = target.label;
      const pick = await vscode.window.showQuickPick([
        { label: `Drop to '${name}'`, operation: 'move' },
        { label: `Drop after '${name}'`, description: 'Shift', operation: 'after' },
        { label: `Reuse in '${name}'`, description: 'Ctrl', operation: 'reuse' },
      ], { title: 'Drop', placeHolder: `Drop ${sources.length === 1 ? this.items.get(sources[0])?.label : sources.length + ' nodes'}` });
      if (pick) {
        await this.client.request('explorer/drop', { sources, target: target.id, operation: pick.operation });
      }
      return;
    }
    const uris = dataTransfer.get('text/uri-list');
    if (uris && (target.kind === 'project' || target.kind === 'resource')) {
      const text = await uris.asString();
      const paths = text.split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith('#'))
        .map(l => vscode.Uri.parse(l)).filter(u => u.scheme === 'file').map(u => u.fsPath);
      if (paths.length) {
        await this.client.request('explorer/dropFiles', { paths, target: target.id });
        await this.refresh([target.id]);
      }
    }
  }

  private click(id: string): void {
    const now = Date.now();
    const item = this.items.get(id);
    if (this.lastClick && this.lastClick.id === id && now - this.lastClick.time < DOUBLE_CLICK_MS) {
      this.lastClick = undefined;
      if (item) {
        void vscode.commands.executeCommand('requality.open', item);
      }
      return;
    }
    this.lastClick = { id, time: now };
    if (item) {
      // An already selected item does not fire onDidChangeSelection; Properties must still follow it.
      this.selectionEmitter.fire(this.view.selection.length ? [...this.view.selection] : [item]);
    }
  }

  dispose(): void {
    for (const s of this.subs) {
      s.dispose();
    }
    this.changeEmitter.dispose();
    this.selectionEmitter.dispose();
    this.titleEmitter.dispose();
  }
}
