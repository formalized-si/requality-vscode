// Smoke test of the launcher of the Requality MCP server (dist/server/bin/requality-mcp, .cmd on Windows): its options,
// and two sessions in an empty folder. In the first one an agent creates a project, imports a document, makes
// requirements, generates a report and deletes a requirement. In the second one it reverts that delete. Run after the
// server is built:
//   node test/mcp/smoke.js [<server folder>] [--command <the command to start, for example an installed wrapper>]
// The default server folder is dist/server.
const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const readline = require('readline');

const args = process.argv.slice(2);
const ci = args.indexOf('--command');
const given = ci >= 0 ? args.splice(ci, 2)[1] : null;
const server = path.resolve(args[0] || path.join(__dirname, '..', '..', 'dist', 'server'));
const win = process.platform === 'win32';
const command = given ? path.resolve(given) : path.join(server, 'bin', win ? 'requality-mcp.cmd' : 'requality-mcp');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-mcp-smoke-'));
const catalogs = path.join(work, 'catalogs');
const storage = path.join(work, 'storage');
fs.mkdirSync(catalogs);

function check(cond, what) {
  if (!cond) {
    throw new Error(what);
  }
  console.log(`ok - ${what}`);
}

/** The command and its arguments for spawn: a .cmd file runs in cmd.exe, which needs the quotes of paths with spaces. */
function line(argv) {
  const q = a => (win && /\s/.test(a) ? `"${a}"` : a);
  return [q(command), argv.map(q)];
}

/** Runs the command with the arguments and no MCP input; returns its exit code, standard output and error. */
function run(argv) {
  const [cmd, a] = line(argv);
  const r = spawnSync(cmd, a, { encoding: 'utf8', shell: win, timeout: 120_000 });
  return { code: r.status, out: r.stdout || '', err: r.stderr || '' };
}

/** A server process with the MCP client side: rpc, tool and end. */
function session() {
  const [cmd, a] = line(['--storage', storage, catalogs]);
  const proc = spawn(cmd, a, { stdio: ['pipe', 'pipe', 'pipe'], shell: win });
  const s = { stderr: '', proc };
  proc.stderr.on('data', d => { s.stderr += d; });
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
  s.rpc = (method, params) => {
    const id = ++nextId;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${method}: no answer in 180 s`)), 180_000);
      pending.set(id, m => { clearTimeout(timer); resolve(m); });
      proc.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
    });
  };
  s.tool = async (name, a) => {
    const m = await s.rpc('tools/call', { name, arguments: a });
    if (m.error || m.result.isError) {
      throw new Error(`${name}: ${JSON.stringify(m.error || m.result.content)}`);
    }
    return JSON.parse(m.result.content[0].text);
  };
  s.start = async () => {
    const init = await s.rpc('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'smoke', version: '0' } });
    proc.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
    return init.result;
  };
  s.end = async () => {
    proc.stdin.end();
    return new Promise(r => proc.on('exit', r));
  };
  return s;
}

let current = null;
(async () => {
  // the options
  const version = run(['--version']);
  check(version.code === 0 && /^requality-mcp \d+\.\d+\.\d+/.test(version.out),
    `--version: ${version.out.trim()}${version.code === 0 ? '' : ` (exit code ${version.code}: ${version.err.trim()})`}`);
  const help = run(['--help']);
  check(help.code === 0 && help.out.includes('--allow'), '--help');
  const bad = run(['--no-such-option']);
  check(bad.code === 2 && bad.err.includes('Unknown option'), 'an unknown option: exit code 2');
  check(run([path.join(work, 'no-such-folder')]).code === 2, 'a folder that does not exist: exit code 2');

  // the first session
  let s = current = session();
  const info = await s.start();
  check(info.serverInfo.name === 'requality', `initialize: ${info.serverInfo.name} ${info.serverInfo.version}`);
  const tools = (await s.rpc('tools/list', {})).result.tools.map(t => t.name);
  check(tools.includes('apply_changes') && tools.includes('revert_change'), `tools/list: ${tools.length} tools`);
  check((await s.tool('create_project', { name: 'Smoke' })).project === 'Smoke', 'create_project');
  check(fs.existsSync(path.join(catalogs, 'Smoke', '.project')), 'the catalog is in the folder');
  const spec = path.join(catalogs, 'spec.html');
  fs.writeFileSync(spec, '<html><body><p>The system shall start.</p><p>The system shall stop.</p></body></html>');
  const doc = await s.tool('import_document', { project: 'Smoke', file: spec });
  check(doc.path === '/Documents/spec.xhtml', 'import_document');
  await s.tool('apply_changes', { project: 'Smoke', summary: 'Requirements', operations: [
    { op: 'create', parent: '/Requirements', id: 'R1', name: 'Start', as: 'r1' },
    { op: 'create', parent: '/Requirements', id: 'R2', name: 'Stop', as: 'r2' },
    { op: 'locate', requirement: '$r1', document: doc.path, quote: 'The system shall start.' },
    { op: 'locate', requirement: '$r2', document: doc.path, quote: 'The system shall stop.' },
    { op: 'link', from: '$r1', to: '$r2' }] });
  const r2 = await s.tool('get_node', { project: 'Smoke', node: '/Requirements/R2' });
  await s.tool('apply_changes', { project: 'Smoke', dry_run: true, operations: [
    { op: 'move', node: '/Requirements/R2', parent: '/Requirements/R1' }] });
  const unchanged = await s.tool('get_node', { project: 'Smoke', node: '/Requirements/R2' });
  check(unchanged.uuid === r2.uuid && unchanged.locations.length === 1,
    'a dry-run move preserves the node, its UUID and its location after reload');
  const searchArgs = { project: 'Smoke', type: 'Requirement', under: '/Requirements', limit: 1 };
  const first = await s.tool('search_nodes', searchArgs);
  const next = await s.tool('search_nodes', { ...searchArgs, offset: first.next_offset });
  check(first.next_offset === 1 && first.matches.length === 1 && next.matches.length === 1
    && first.matches[0].uuid !== next.matches[0].uuid && first.total === next.total,
    'search pages keep their size, total and distinct nodes');
  const coverageArgs = { project: 'Smoke', documents: false, limit: 1 };
  const gap1 = (await s.tool('trace_coverage', coverageArgs)).requirements.leaves_without_test_purposes;
  const gap2 = (await s.tool('trace_coverage', { ...coverageArgs, offset: gap1.next_offset })).requirements.leaves_without_test_purposes;
  check(gap1.next_offset === 1 && gap1.total === 2 && gap2.total === 2 && !('next_offset' in gap2)
    && gap1.items[0].path !== gap2.items[0].path, 'coverage pages reach the last gap without duplicates');
  // a report without the HTTP server of the web editors (the headless server starts none)
  const out = path.join(catalogs, 'report');
  const report = await s.tool('generate_report', { project: 'Smoke', template: 'com.unitesk.requality.reports.tableview', output: out });
  check(report.files.length >= 1 && fs.statSync(report.files[0]).size > 500, `generate_report: ${report.files.length} file(s)`);
  const del = await s.tool('apply_changes', { project: 'Smoke', summary: 'Remove R2', operations: [{ op: 'delete', node: '/Requirements/R2' }] });
  check((await s.end()) === 0, 'the first session ends with exit code 0');

  // the second session: the change of the first one is reverted, with the UUID of the deleted node
  s = current = session();
  await s.start();
  const review = await s.tool('review_changes', { project: 'Smoke' });
  check(review.changes[0].change === del.change && review.changes[0].session === 'earlier', 'review_changes shows the earlier session');
  await s.tool('revert_change', { project: 'Smoke', change: del.change });
  const back = await s.tool('get_node', { project: 'Smoke', node: '/Requirements/R2' });
  check(back.uuid === r2.uuid && back.locations.length === 1, 'revert_change brings R2 back with its UUID and location');
  const r1 = await s.tool('get_node', { project: 'Smoke', node: '/Requirements/R1' });
  check(JSON.stringify(r1.links).includes('/Requirements/R2'), 'the link to R2 resolves again');
  check((await s.end()) === 0, 'the second session ends with exit code 0');
})().catch(e => {
  console.error(e.message);
  if (current) {
    console.error(current.stderr.split('\n').slice(-40).join('\n'));
    current.proc.kill();
  }
  process.exit(1);
});
