import * as fs from 'fs';
import * as path from 'path';
import { JSDOM } from 'jsdom';

/** The renderer under test, loaded from the source tree (test/unit/ compiles to out/test/unit/). */
const RENDERER_PATH = path.resolve(__dirname, '..', '..', '..', 'media', 'rswt', 'renderer.js');
const RENDERER_SOURCE = fs.readFileSync(RENDERER_PATH, 'utf8');

/** A message the renderer posted through acquireVsCodeApi().postMessage. */
export interface Posted {
  type: string;
  [key: string]: any;
}

export interface Op {
  op: 'create' | 'set' | 'dispose' | 'call';
  [key: string]: any;
}

export interface Harness {
  /** The jsdom window the renderer runs in. */
  window: any;
  document: Document;
  /** Every message posted so far, including 'ready' sent on load. */
  posted: Posted[];
  /** Only the widget events ({type:'event'}) posted so far. */
  events(): Posted[];
  /** Dispatches a message to the renderer the way the webview host delivers it. */
  send(message: object): void;
  /** Sends a 'snapshot' message (replaces all widgets). */
  snapshot(ops: Op[]): void;
  /** Sends an 'update' message (incremental). */
  update(ops: Op[]): void;
  /** Returns the DOM element of a widget (by its server id). */
  el(id: string): HTMLElement;
  /** Returns the DOM element of a widget or null when it is not in the document. */
  find(id: string): HTMLElement | null;
}

/** Creates a fresh JSDOM window with the renderer loaded. Each test gets its own window. */
export function createHarness(): Harness {
  const dom = new JSDOM('<!DOCTYPE html><html><head></head><body><div id="root"></div></body></html>', {
    runScripts: 'outside-only',
    pretendToBeVisual: true,
  });
  const window = dom.window as any;
  const posted: Posted[] = [];
  window.acquireVsCodeApi = () => ({
    // Copied into this realm: the renderer's objects belong to the jsdom window's realm.
    postMessage: (m: Posted) => { posted.push(JSON.parse(JSON.stringify(m))); },
    getState: () => undefined,
    setState: () => undefined,
  });
  window.eval(RENDERER_SOURCE);
  const document: Document = window.document;

  const harness: Harness = {
    window,
    document,
    posted,
    events: () => posted.filter(m => m.type === 'event'),
    send: (message: object) => {
      window.dispatchEvent(new window.MessageEvent('message', { data: message }));
    },
    snapshot: (ops: Op[]) => harness.send({ type: 'snapshot', ops, images: {} }),
    update: (ops: Op[]) => harness.send({ type: 'update', ops }),
    el: (id: string) => {
      const found = harness.find(id);
      if (!found) throw new Error(`no DOM element for widget ${id}`);
      return found;
    },
    find: (id: string) => document.querySelector<HTMLElement>(`[data-wid="${id}"]`),
  };
  return harness;
}

// Operation builders (the shapes the server sends; see src/ui/rswtHost.ts and the renderer's apply()).

export function create(id: string, type: string, parent: string | null, props: Record<string, unknown> = {}, style = 0): Op {
  return { op: 'create', id, type, parent, style, props };
}

export function set(id: string, props: Record<string, unknown>): Op {
  return { op: 'set', id, props };
}

export function dispose(id: string): Op {
  return { op: 'dispose', id };
}

export function call(id: string, method: string, args?: unknown): Op {
  return { op: 'call', id, method, args };
}
