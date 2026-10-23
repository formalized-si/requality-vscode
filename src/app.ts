import * as vscode from 'vscode';
import { RequalityClient, RequalityError, ErrorCodes } from './server/connection';
import { ExplorerItem, RequalityExplorer, nodeIdOf } from './views/explorer';
import { RswtHost } from './ui/rswtHost';

/**
 * Shared services of the extension, handed to every feature module.
 */
export interface App {
  readonly context: vscode.ExtensionContext;
  readonly client: RequalityClient;
  readonly log: vscode.OutputChannel;
  readonly explorer: RequalityExplorer;
  readonly selection: SelectionHub;
  readonly ui: RswtHost;
}

/** Where the current selection comes from (Eclipse EventSource). */
export type SelectionSource = 'explorer' | 'editor' | 'links' | 'outline' | 'todo';

export interface Selection {
  source: SelectionSource;
  /** Node ids; a project item counts as its root node. */
  nodes: string[];
  /** The selected items as the source view knows them (project items keep their "project:" id). */
  items?: string[];
}

/**
 * The workbench selection that Properties and the Links Explorer follow (Eclipse ISelectionService).
 */
export class SelectionHub implements vscode.Disposable {
  private readonly emitter = new vscode.EventEmitter<Selection>();
  readonly onDidChange = this.emitter.event;
  private current: Selection = { source: 'explorer', nodes: [] };

  get value(): Selection {
    return this.current;
  }

  set(source: SelectionSource, nodes: string[], items?: string[]): void {
    this.current = { source, nodes, items: items ?? nodes };
    this.emitter.fire(this.current);
  }

  dispose(): void {
    this.emitter.dispose();
  }
}

/**
 * Nodes a command applies to: the clicked item and the multi-selection (VS Code passes both), else the explorer
 * selection. Project items count as their root node.
 */
export function commandNodes(app: App, item?: ExplorerItem | string, items?: ExplorerItem[]): string[] {
  if (Array.isArray(items) && items.length > 0) {
    return items.map(nodeIdOf).filter((x): x is string => !!x);
  }
  if (typeof item === 'string') {
    return [item];
  }
  if (item && typeof item === 'object' && 'id' in item) {
    const id = nodeIdOf(item);
    return id ? [id] : [];
  }
  const sel = app.selection.value;
  if (sel.nodes.length) {
    return sel.nodes;
  }
  return app.explorer.selectedNodeIds();
}

/** Runs a command body and reports failures the way Eclipse handlers report them (error dialog). */
export function run<T extends unknown[]>(app: App, title: string, body: (...args: T) => unknown | Promise<unknown>) {
  return async (...args: T) => {
    try {
      return await body(...args);
    } catch (e) {
      if (e instanceof RequalityError && e.code === ErrorCodes.cancelled) {
        return undefined;
      }
      const msg = e instanceof Error ? e.message : String(e);
      app.log.appendLine(`[${title}] ${e instanceof Error ? e.stack ?? msg : msg}`);
      void vscode.window.showErrorMessage(`${title}: ${msg}`);
      return undefined;
    }
  };
}

/** Registers a command whose failures are reported to the user. */
export function command(app: App, id: string, body: (...args: any[]) => unknown | Promise<unknown>): vscode.Disposable {
  return vscode.commands.registerCommand(id, run(app, id, body));
}
