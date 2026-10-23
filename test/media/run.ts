import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as http from 'http';
import * as net from 'net';
import * as os from 'os';
import * as path from 'path';
import { runTests } from '@vscode/test-electron';

/**
 * Records the pictures of the README (media/readme): VS Code with the extension on the ETSI TS 103 096 catalog, driven
 * by test/media/index.ts. A picture is a screenshot of the VS Code window through the Chrome DevTools Protocol, so the
 * window does not have to be visible on the screen. ffmpeg makes the GIF of the frames.
 */

const WIDTH = 1280;
const HEIGHT = 800;
/** The height of the title bar of the window. */
const TITLE_BAR = 35;

/** The frames of the GIF and the time each one is shown (seconds). */
const GIF_FRAMES: Array<[string, number]> = [
  ['01-explorer', 1.6], ['02-project', 1.6], ['03-requirements', 1.6], ['04-properties', 2.4],
  ['05-context-menu', 2.4], ['06-document-editor', 2.8], ['07-unieditor', 2.8],
];
/** The screenshots of the README: frame, file. */
const SCREENSHOTS: Array<[string, string]> = [
  ['04-properties', 'explorer-properties.png'], ['05-context-menu', 'context-menu.png'],
  ['06-document-editor', 'document-editor.png'], ['07-unieditor', 'unieditor.png'],
];

function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.listen(0, '127.0.0.1', () => {
      const port = (server.address() as net.AddressInfo).port;
      server.close(() => resolve(port));
    });
    server.on('error', reject);
  });
}

/** A Chrome DevTools Protocol connection. */
class Cdp {
  private next = 1;
  private readonly pending = new Map<number, (m: { result?: unknown; error?: { message: string } }) => void>();

  private constructor(private readonly socket: WebSocket) {
    socket.addEventListener('message', ev => {
      const m = JSON.parse(String(ev.data));
      const done = this.pending.get(m.id);
      if (done) {
        this.pending.delete(m.id);
        done(m);
      }
    });
  }

  static connect(url: string): Promise<Cdp> {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(url);
      socket.addEventListener('open', () => resolve(new Cdp(socket)));
      socket.addEventListener('error', () => reject(new Error(`no DevTools connection at ${url}`)));
    });
  }

  send<T>(method: string, params: object = {}): Promise<T> {
    const id = this.next++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, m => (m.error ? reject(new Error(`${method}: ${m.error.message}`)) : resolve(m.result as T)));
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  close(): void {
    this.socket.close();
  }
}

async function json<T>(url: string): Promise<T> {
  const response = await fetch(url);
  return (await response.json()) as T;
}

/** The page of the VS Code workbench and the browser, through the DevTools port. */
async function connect(port: number): Promise<{ page: Cdp }> {
  const base = `http://127.0.0.1:${port}`;
  const targets = await json<Array<{ id: string; type: string; url: string; webSocketDebuggerUrl: string }>>(`${base}/json/list`);
  const workbench = targets.find(t => t.type === 'page' && /workbench/.test(t.url));
  if (!workbench) {
    throw new Error(`no workbench page in ${JSON.stringify(targets.map(t => t.url))}`);
  }
  return { page: await Cdp.connect(workbench.webSocketDebuggerUrl) };
}

async function main(): Promise<void> {
  const root = path.resolve(__dirname, '..', '..', '..');
  const catalog = path.join(root, 'server', 'build', 'test-catalogs', 'etsi', 'TS103096');
  if (!fs.existsSync(catalog)) {
    throw new Error(`${catalog} is missing: run "npm run test:server" first (it downloads the catalog).`);
  }
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-media-'));
  const workspace = path.join(work, 'workspace');
  const frames = path.join(work, 'frames');
  fs.mkdirSync(workspace, { recursive: true });
  fs.mkdirSync(frames, { recursive: true });
  const userSettings = path.join(work, 'user-data', 'User');
  fs.mkdirSync(userSettings, { recursive: true });
  fs.writeFileSync(path.join(userSettings, 'settings.json'), JSON.stringify({
    'requality.explorer.view': 'eclipse',
    'workbench.colorTheme': 'Default Dark Modern',
    'workbench.startupEditor': 'none',
    'workbench.tips.enabled': false,
    'chat.disableAIFeatures': true,
    'workbench.secondarySideBar.defaultVisibility': 'hidden',
    'window.commandCenter': false,
  }, null, 2));
  const cdpPort = await freePort();
  let cdp: { page: Cdp } | undefined;
  const server = http.createServer((req, res) => {
    void (async () => {
      const url = new URL(req.url ?? '/', 'http://localhost');
      try {
        cdp ??= await connect(cdpPort);
        if (url.pathname === '/window') {
          // the workbench is laid out and painted at the size of the pictures, whatever the size of the window
          await cdp.page.send('Emulation.setDeviceMetricsOverride', { width: WIDTH, height: HEIGHT, deviceScaleFactor: 1, mobile: false });
        } else if (url.pathname === '/capture') {
          const name = url.searchParams.get('name') ?? 'frame';
          const shot = await cdp.page.send<{ data: string }>('Page.captureScreenshot', { format: 'png' });
          fs.writeFileSync(path.join(frames, `${name}.png`), Buffer.from(shot.data, 'base64'));
          console.log(`  captured ${name}`);
        }
        res.writeHead(200).end('ok');
      } catch (e) {
        console.error(e);
        res.writeHead(500).end(String(e));
      }
    })();
  });
  const httpPort = await new Promise<number>(resolve => server.listen(0, '127.0.0.1', () => resolve((server.address() as net.AddressInfo).port)));
  try {
    await runTests({
      version: process.env.VSCODE_TEST_VERSION ?? 'stable',
      extensionDevelopmentPath: root,
      extensionTestsPath: path.join(__dirname, 'index'),
      // no other extensions (an empty extensions folder: --disable-extensions would show a notification)
      launchArgs: [workspace, '--extensions-dir', path.join(work, 'extensions'), '--disable-workspace-trust', '--skip-welcome',
        '--skip-release-notes', '--user-data-dir', path.join(work, 'user-data'), `--remote-debugging-port=${cdpPort}`],
      extensionTestsEnv: {
        REQUALITY_MEDIA_URL: `http://127.0.0.1:${httpPort}`,
        REQUALITY_IT_WORKSPACE: workspace,
        REQUALITY_IT_CATALOG: catalog,
      },
    });
  } finally {
    cdp?.page.close();
    server.close();
  }
  const out = path.join(root, 'media', 'readme');
  fs.mkdirSync(out, { recursive: true });
  // the title bar of the window (the Extension Development Host) is cut from the pictures
  const crop = `crop=${WIDTH}:${HEIGHT - TITLE_BAR}:0:${TITLE_BAR}`;
  for (const [frame, file] of SCREENSHOTS) {
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', path.join(frames, `${frame}.png`), '-vf', crop, path.join(out, file)]);
  }
  // the GIF: the frames for their times, 960 pixels wide, with one palette made of all of them
  const list = path.join(frames, 'frames.txt');
  fs.writeFileSync(list, GIF_FRAMES.map(([f, t]) => `file '${path.join(frames, f + '.png')}'\nduration ${t}\n`).join('')
    + `file '${path.join(frames, GIF_FRAMES[GIF_FRAMES.length - 1][0] + '.png')}'\n`);
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', list,
    '-vf', `${crop},scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=none`,
    '-loop', '0', path.join(out, 'overview.gif')]);
  fs.rmSync(work, { recursive: true, force: true });
  console.log(`pictures in ${out}`);
}

main().catch(e => {
  console.error(e instanceof Error ? e.message : e);
  process.exit(1);
});
