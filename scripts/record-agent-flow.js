// Record a real MCP workflow. No model conversation or client UI is fabricated: the exported transcript contains
// every request and server response, and the animation identifies itself as a scripted MCP client.
const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const readline = require('readline');
const assert = require('assert/strict');

const root = path.resolve(__dirname, '..');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-agent-demo-'));
const catalog = path.join(work, 'catalogs');
fs.mkdirSync(catalog);
const output = path.join(work, 'output');
fs.mkdirSync(output);
const site = process.env.REQUALITY_MEDIA_SITE && path.resolve(process.env.REQUALITY_MEDIA_SITE);
const siteAssets = site && path.join(site, 'dist/projects/requality-vscode/assets');
if (site && (!fs.existsSync(path.join(site, '.openai/hosting.json')) || !fs.existsSync(siteAssets))) {
  throw new Error('REQUALITY_MEDIA_SITE must point to the existing Formalized site checkout');
}
const win = process.platform === 'win32';
const command = path.join(root, 'dist/server/bin', win ? 'requality-mcp.cmd' : 'requality-mcp');
const argv = ['--storage', path.join(work, 'storage'), catalog];
const child = spawn(win ? `"${command}"` : command, win ? argv.map(a => `"${a}"`) : argv,
  { stdio: ['pipe', 'pipe', 'pipe'], shell: win });
const exited = new Promise(resolve => child.on('exit', code => resolve(code)));
const pending = new Map();
let next = 0;
let stderr = '';
const transcript = [];
const scenes = [];
child.stderr.on('data', data => { stderr += data; });
const rejectPending = error => {
  for (const entry of pending.values()) { clearTimeout(entry.timer); entry.reject(error); }
  pending.clear();
};
child.on('error', rejectPending);
child.on('exit', code => rejectPending(new Error(`MCP server exited with ${code}`)));
readline.createInterface({ input: child.stdout }).on('line', line => {
  try {
    const response = JSON.parse(line);
    const entry = pending.get(response.id);
    if (entry) { pending.delete(response.id); clearTimeout(entry.timer); entry.resolve(response); }
  } catch (error) { rejectPending(error); }
});
async function rpc(method, params) {
  const request = { jsonrpc: '2.0', id: ++next, method, params };
  const response = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(request.id); reject(new Error(`${method} timed out`)); }, 120000);
    pending.set(request.id, { resolve, reject, timer });
    child.stdin.write(JSON.stringify(request) + '\n');
  });
  transcript.push({ request, response });
  if (response.error || response.result?.isError) throw new Error(JSON.stringify(response));
  return response.result;
}
async function tool(name, args) {
  console.log(`MCP > ${name}`);
  const result = await rpc('tools/call', { name, arguments: args });
  return result.structuredContent || JSON.parse(result.content.find(c => c.type === 'text').text);
}
function scene(title, intent, calls, result) { scenes.push({ title, intent, calls, result }); }
function normalized(value) {
  let json = JSON.stringify(value);
  for (const prefix of [...new Set([fs.realpathSync(work), work])].sort((a, b) => b.length - a.length))
    json = json.split(prefix).join('$DEMO');
  return JSON.parse(json);
}

(async () => {
  const info = await rpc('initialize', { protocolVersion: '2025-06-18', capabilities: {},
    clientInfo: { name: 'requality-agent-demo', version: '1' } });
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
  const { tools } = await rpc('tools/list', {});
  assert(tools.some(t => t.name === 'update_document_version'));
  scene('Connect to Requality', 'Discover the tools before changing the catalog.', ['initialize', 'tools/list'],
    { server: info.serverInfo.name, version: info.serverInfo.version, tools: tools.length, transport: 'stdio' });

  await tool('create_project', { name: 'Brakes' });
  const file = path.join(catalog, 'brakes.html');
  const originalText = 'The braking system shall stop the vehicle within 60 m.';
  const logText = 'The system shall record every braking event.';
  fs.writeFileSync(file, `<html><body><p>${originalText}</p><p>${logText}</p></body></html>`);
  const doc = await tool('import_document', { project: 'Brakes', file });
  scene('Import the specification', 'Bring the source document into a new catalog.', ['create_project', 'import_document'],
    { project: 'Brakes', document: doc.path });
  const read = await tool('read_document', { project: 'Brakes', document: doc.path });
  assert(JSON.stringify(read).includes(originalText));
  scene('Read before editing', 'Extract requirements from the actual source paragraphs.', ['read_document'],
    { paragraphs: read.paragraphs });

  const edit = await tool('apply_changes', { project: 'Brakes', summary: 'Extract braking requirements', operations: [
    { op: 'create', parent: '/Requirements', id: 'BRAKE', name: 'Stopping distance', as: 'brake' },
    { op: 'create', parent: '/Requirements', id: 'LOG', name: 'Braking event log', as: 'log' },
    { op: 'locate', requirement: '$brake', document: doc.path, quote: originalText },
    { op: 'locate', requirement: '$log', document: doc.path, quote: logText }
  ] });
  const brake = await tool('get_node', { project: 'Brakes', node: '/Requirements/BRAKE' });
  assert.equal(brake.locations.length, 1);
  scene('Create traceable requirements', 'Create both requirements and link them to their source in one change.',
    ['apply_changes  // 2 create + 2 locate', 'get_node /Requirements/BRAKE'],
    { change: edit.change, requirement: brake.path, source: brake.locations[0] });
  const coverage = await tool('trace_coverage', { project: 'Brakes' });
  scene('Check coverage', 'Check source coverage and expose missing test purposes.', ['trace_coverage'], coverage);

  const versionDir = path.join(catalog, 'v2');
  fs.mkdirSync(versionDir);
  const newerFile = path.join(versionDir, 'brakes.html');
  const changedText = originalText.replace('60 m', '55 m');
  fs.writeFileSync(newerFile, `<html><body><p>${changedText}</p><p>${logText}</p>`
    + '<p>The system shall monitor brake pressure.</p></body></html>');
  const update = await tool('update_document_version', { project: 'Brakes', document: doc.path, file: newerFile });
  assert.equal(update.summary.changed, 1);
  assert.equal(update.summary.unchanged, 1);
  assert.equal(update.summary.new_sentences, 1);
  scene('Review a new specification', 'Detect the 60 m → 55 m change and the new, untraced statement.',
    ['update_document_version'], update);
  const review = await tool('review_changes', { project: 'Brakes', change: update.change });
  assert(JSON.stringify(review).includes('55 m'));
  scene('Inspect the recorded change', 'Read what changed before accepting it.', ['review_changes'], review);

  const reverted = await tool('revert_change', { project: 'Brakes', change: update.change });
  const restored = await tool('get_node', { project: 'Brakes', node: '/Requirements/BRAKE' });
  assert.equal(restored.uuid, brake.uuid);
  assert.equal(restored.locations.length, 1);
  assert.equal(restored.locations[0].document, doc.path);
  const restoredDoc = await tool('read_document', { project: 'Brakes', document: doc.path });
  assert(JSON.stringify(restoredDoc).includes(originalText));
  assert.equal(restoredDoc.superseded_by, undefined);
  scene('Revert and verify', 'Restore the original source links while keeping the requirement UUID.',
    ['revert_change', 'get_node /Requirements/BRAKE', 'read_document'],
    { reverted_change: update.change, revert: reverted.change, requirement: restored.path,
      source: restored.locations[0].document, uuid_preserved: restored.uuid === brake.uuid,
      original_text: originalText });
  child.stdin.end();
  assert.equal(await exited, 0);
  const recording = normalized({ client: 'Scripted MCP client; real server requests and responses',
    recorded_at: new Date().toISOString(), server: info.serverInfo, scenes, transcript });
  const json = path.join(output, 'agent-flow.json');
  fs.writeFileSync(json, JSON.stringify(recording, null, 2) + '\n');
  const render = spawnSync('python3', [path.join(__dirname, 'render-agent-flow.py'), json, output], { stdio: 'inherit' });
  if (render.status !== 0) throw new Error(`Renderer exited with ${render.status}. Install Pillow for python3.`);
  for (const destination of [path.join(root, 'media/readme'), siteAssets].filter(Boolean)) {
    for (const name of ['agent-flow.json', 'agent-flow.png', 'agent-flow.gif'])
      fs.copyFileSync(path.join(output, name), path.join(destination, name));
    console.log(`Agent workflow saved in ${destination}`);
  }
  fs.rmSync(work, { recursive: true, force: true });
})().catch(error => {
  child.kill();
  console.error(error.message);
  console.error(`Recording retained in ${work}`);
  if (stderr) fs.writeFileSync(path.join(work, 'server.log'), stderr);
  process.exitCode = 1;
});
