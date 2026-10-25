// Smoke test of the launcher of the Requality MCP server (dist/server/bin/requality-mcp, .cmd on Windows): in an empty
// folder an agent creates a project and a requirement and reads it back. Run after the server is built:
//   node test/mcp/smoke.js [<server folder>]   (default: dist/server)
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const readline = require('readline');

const server = path.resolve(process.argv[2] || path.join(__dirname, '..', '..', 'dist', 'server'));
const win = process.platform === 'win32';
const command = path.join(server, 'bin', win ? 'requality-mcp.cmd' : 'requality-mcp');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-mcp-smoke-'));
const catalogs = path.join(work, 'catalogs');
fs.mkdirSync(catalogs);

const proc = spawn(command, ['--storage', path.join(work, 'storage'), catalogs], { stdio: ['pipe', 'pipe', 'pipe'], shell: win });
let stderr = '';
proc.stderr.on('data', d => { stderr += d; });
const pending = new Map();
readline.createInterface({ input: proc.stdout }).on('line', line => {
  const msg = JSON.parse(line);
  const p = pending.get(msg.id);
  if (p) {
    pending.delete(msg.id);
    p(msg);
  }
});
let nextId = 0;
function rpc(method, params) {
  const id = ++nextId;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${method}: no answer in 180 s`)), 180_000);
    pending.set(id, m => { clearTimeout(timer); resolve(m); });
    proc.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
  });
}
async function tool(name, args) {
  const m = await rpc('tools/call', { name, arguments: args });
  if (m.error || m.result.isError) {
    throw new Error(`${name}: ${JSON.stringify(m.error || m.result.content)}`);
  }
  return JSON.parse(m.result.content[0].text);
}
function check(cond, what) {
  if (!cond) {
    throw new Error(what);
  }
  console.log(`ok - ${what}`);
}

(async () => {
  const init = await rpc('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'smoke', version: '0' } });
  check(init.result.serverInfo.name === 'requality', `initialize: ${init.result.serverInfo.name} ${init.result.serverInfo.version}`);
  proc.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
  const tools = (await rpc('tools/list', {})).result.tools.map(t => t.name);
  check(tools.includes('apply_changes') && tools.includes('read_document'), `tools/list: ${tools.length} tools`);
  check((await tool('create_project', { name: 'Smoke' })).project === 'Smoke', 'create_project');
  await tool('apply_changes', { project: 'Smoke', summary: 'First requirement', operations: [
    { op: 'create', parent: '/Requirements', id: 'R1', name: 'First requirement', description: 'The system shall start.' }] });
  const node = await tool('get_node', { project: 'Smoke', node: '/Requirements/R1' });
  check(node.name === 'First requirement' && node.description === 'The system shall start.', 'apply_changes and get_node');
  check(fs.existsSync(path.join(catalogs, 'Smoke', '.project')), 'the catalog is in the folder');
  proc.stdin.end();
  const code = await new Promise(r => proc.on('exit', r));
  check(code === 0, `exit code ${code}`);
})().catch(e => {
  console.error(e.message);
  console.error(stderr.split('\n').slice(-40).join('\n'));
  proc.kill();
  process.exit(1);
});
