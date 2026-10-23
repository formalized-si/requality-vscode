import { ChildProcess, spawn } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import {
  createMessageConnection, MessageConnection, ResponseError, StreamMessageReader, StreamMessageWriter,
} from 'vscode-jsonrpc/node';
import { findJava } from './java';
import { explorerViewMode } from '../views/explorerMode';

export type ServerState = 'stopped' | 'starting' | 'ready' | 'failed';

/** Error codes of the Requality server (RpcException). */
export const ErrorCodes = {
  invalidParams: -32602,
  internal: -32603,
  notFound: -32001,
  notReady: -32002,
  refused: -32003,
  cancelled: -32800,
} as const;

export class RequalityError extends Error {
  constructor(readonly code: number, message: string, readonly data?: unknown) {
    super(message);
  }
}

type RequestHandler = (params: any) => unknown | Promise<unknown>;
type NotificationHandler = (params: any) => void;

/**
 * Runs the Requality server (Java) and exchanges JSON-RPC messages with it over stdio.
 */
export class RequalityClient implements vscode.Disposable {
  private proc: ChildProcess | undefined;
  private conn: MessageConnection | undefined;
  private readonly requestHandlers = new Map<string, RequestHandler>();
  private readonly notificationHandlers = new Map<string, Set<NotificationHandler>>();
  private readonly stateEmitter = new vscode.EventEmitter<ServerState>();
  private currentState: ServerState = 'stopped';
  private starting: Promise<void> | undefined;
  private stopping = false;
  readonly onDidChangeState = this.stateEmitter.event;

  constructor(private readonly context: vscode.ExtensionContext, readonly log: vscode.OutputChannel) {}

  get state(): ServerState {
    return this.currentState;
  }

  private setState(s: ServerState): void {
    this.currentState = s;
    void vscode.commands.executeCommand('setContext', 'requality.serverState', s);
    this.stateEmitter.fire(s);
  }

  /** Registers a handler for a request sent by the server (client/*). */
  onRequest(method: string, handler: RequestHandler): vscode.Disposable {
    if (this.requestHandlers.has(method)) {
      throw new Error(`Duplicate request handler ${method}`);
    }
    this.requestHandlers.set(method, handler);
    this.conn?.onRequest(method, handler as any);
    return new vscode.Disposable(() => this.requestHandlers.delete(method));
  }

  /** Subscribes to a server notification. Several subscribers per method are allowed. */
  onNotification(method: string, handler: NotificationHandler): vscode.Disposable {
    let set = this.notificationHandlers.get(method);
    if (!set) {
      set = new Set();
      this.notificationHandlers.set(method, set);
      this.conn?.onNotification(method, (p: unknown) => this.dispatchNotification(method, p));
    }
    set.add(handler);
    return new vscode.Disposable(() => set!.delete(handler));
  }

  private dispatchNotification(method: string, params: unknown): void {
    for (const h of this.notificationHandlers.get(method) ?? []) {
      try {
        h(params);
      } catch (e) {
        this.log.appendLine(`[client] notification handler ${method} failed: ${e}`);
      }
    }
  }

  async request<T = unknown>(method: string, params: object = {}): Promise<T> {
    if (this.starting) {
      await this.starting;
    }
    if (!this.conn || this.currentState !== 'ready') {
      throw new RequalityError(ErrorCodes.notReady, 'The Requality server is not running');
    }
    try {
      return await this.conn.sendRequest<T>(method, params);
    } catch (e) {
      if (e instanceof ResponseError) {
        throw new RequalityError(e.code, e.message, e.data);
      }
      throw e;
    }
  }

  notify(method: string, params: object = {}): void {
    if (this.conn && this.currentState === 'ready') {
      void this.conn.sendNotification(method, params);
    }
  }

  start(): Promise<void> {
    if (!this.starting) {
      this.starting = this.doStart().finally(() => {
        this.starting = undefined;
      });
    }
    return this.starting;
  }

  private async doStart(): Promise<void> {
    this.setState('starting');
    try {
      const config = vscode.workspace.getConfiguration('requality');
      // the packages for a platform contain a Java runtime (jre/)
      const java = await findJava(config.get<string>('java.home'), this.context.asAbsolutePath('jre'));
      this.log.appendLine(`[client] Java ${java.version} from ${java.source}: ${java.executable}`);
      const lib = this.context.asAbsolutePath(path.join('dist', 'server', 'lib'));
      const resources = this.context.asAbsolutePath(path.join('dist', 'server', 'resources'));
      if (!fs.existsSync(path.join(lib, 'requality-server.jar'))) {
        throw new Error(`Requality server is missing (${lib}). Build it with "npm run build:server".`);
      }
      const storage = path.join(this.context.globalStorageUri.fsPath, 'server');
      fs.mkdirSync(storage, { recursive: true });
      const args = [
        `-Xmx${config.get<string>('server.maxHeap') || '2g'}`,
        '-Dfile.encoding=UTF-8',
        // headless AWT except on Windows, where the registry fix opens its .reg file with java.awt.Desktop
        ...(process.platform === 'win32' ? [] : ['-Djava.awt.headless=true']),
        '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
        '-cp', path.join(lib, '*'),
        'com.unitesk.requality.vscode.Main',
        '--resources', resources,
        '--storage', storage,
      ];
      this.log.appendLine(`[client] starting ${java.executable} ${args.join(' ')}`);
      const proc = spawn(java.executable, args, { cwd: storage, stdio: ['pipe', 'pipe', 'pipe'] });
      this.proc = proc;
      proc.stderr!.setEncoding('utf8');
      proc.stderr!.on('data', (chunk: string) => this.log.append(chunk));
      proc.on('exit', (code, signal) => {
        this.log.appendLine(`[client] server exited (code ${code}, signal ${signal})`);
        // the exit of a server that was stopped may be reported after the next one started (a restart)
        if (this.proc !== proc) {
          return;
        }
        this.conn?.dispose();
        this.conn = undefined;
        this.proc = undefined;
        if (!this.stopping) {
          this.setState('failed');
          void vscode.window.showErrorMessage('The Requality server stopped unexpectedly.', 'Show Log', 'Restart')
            .then(choice => {
              if (choice === 'Show Log') {
                this.log.show();
              } else if (choice === 'Restart') {
                void vscode.commands.executeCommand('requality.restartServer');
              }
            });
        }
      });
      const spawnError = new Promise<never>((_, reject) => proc.on('error', reject));
      const conn = createMessageConnection(new StreamMessageReader(proc.stdout!), new StreamMessageWriter(proc.stdin!));
      for (const [method, handler] of this.requestHandlers) {
        conn.onRequest(method, handler as any);
      }
      for (const method of this.notificationHandlers.keys()) {
        conn.onNotification(method, (p: unknown) => this.dispatchNotification(method, p));
      }
      conn.onError(([err]) => this.log.appendLine(`[client] connection error: ${err}`));
      conn.listen();
      this.conn = conn;
      const folders = (vscode.workspace.workspaceFolders ?? [])
        .filter(f => f.uri.scheme === 'file').map(f => f.uri.fsPath);
      const result = await Promise.race([
        // The workspace storage keeps the Eclipse workspace metadata of the projects (closed, removed, external).
        conn.sendRequest('initialize', {
          workspaceFolders: folders,
          workspaceStorage: this.context.storageUri?.scheme === 'file' ? this.context.storageUri.fsPath : null,
          settings: initialSettings(),
          editors: vscodeEditors(),
          // the Requality Explorer as the Eclipse view (its tree drawn by the server) or as a native VS Code tree
          explorerView: explorerViewMode(),
        }),
        spawnError,
      ]);
      this.log.appendLine(`[client] server initialized: ${JSON.stringify(result)}`);
      this.setState('ready');
    } catch (e) {
      this.log.appendLine(`[client] start failed: ${e instanceof Error ? e.stack ?? e.message : e}`);
      this.killProcess();
      this.setState('failed');
      throw e;
    }
  }

  private killProcess(): void {
    this.conn?.dispose();
    this.conn = undefined;
    if (this.proc && this.proc.exitCode === null) {
      this.proc.kill();
    }
    this.proc = undefined;
  }

  async stop(): Promise<void> {
    this.stopping = true;
    try {
      if (this.conn && this.currentState === 'ready') {
        await Promise.race([this.conn.sendRequest('shutdown'), new Promise(r => setTimeout(r, 5000))]).catch(() => undefined);
        void this.conn.sendNotification('exit');
      }
      await new Promise(r => setTimeout(r, 200));
      this.killProcess();
      this.setState('stopped');
    } finally {
      this.stopping = false;
    }
  }

  async restart(): Promise<void> {
    await this.stop();
    await this.start();
  }

  dispose(): void {
    this.stopping = true;
    this.killProcess();
    this.stateEmitter.dispose();
  }
}

/** Settings the server needs at start-up (Activator reads them from the preference store). */
export function initialSettings(): Record<string, unknown> {
  const c = vscode.workspace.getConfiguration('requality');
  return {
    libreOfficePath: c.get('libreOfficePath'),
    imageMagickPath: c.get('imageMagickPath'),
    globalCacheLocation: c.get('globalCacheLocation'),
    debugLevel: c.get('debugLevel'),
    explorerFilters: c.get('explorer.filters'),
    // the open mode of Window > Preferences > General (Double click / Single click)
    listOpenMode: vscode.workspace.getConfiguration('workbench.list').get('openMode'),
  };
}

/** A custom editor of an installed extension (contributes.customEditors), for the editor registry of the server. */
interface EditorContribution {
  id: string;
  label: string;
  filenamePatterns: string[];
}

/**
 * The editors of VS Code the Eclipse editor registry offers in Open With (OpenWithMenu, EditorSelectionDialog): the
 * custom editors contributed by the installed extensions, with their file name patterns.
 */
export function vscodeEditors(): EditorContribution[] {
  const out: EditorContribution[] = [];
  for (const ext of vscode.extensions.all) {
    const editors = (ext.packageJSON?.contributes?.customEditors ?? []) as Array<{ viewType?: string; displayName?: string; selector?: Array<{ filenamePattern?: string }> }>;
    for (const e of editors) {
      if (!e.viewType) {
        continue;
      }
      const patterns = (e.selector ?? []).map(s => s.filenamePattern).filter((p): p is string => typeof p === 'string');
      out.push({ id: e.viewType, label: e.displayName ?? e.viewType, filenamePatterns: patterns });
    }
  }
  return out;
}
