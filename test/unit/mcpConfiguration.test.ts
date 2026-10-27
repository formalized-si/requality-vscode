import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import { mcpConfiguration } from '../../src/server/mcpConfiguration';

test('agent configurations preserve paths, multiple folders and Java options as individual arguments', () => {
  const java = 'C:\\Program Files\\Java\\bin\\java.exe';
  const folders = ['C:\\Требования\\one', 'C:\\specs\\a "quoted" folder'];
  const json = JSON.parse(mcpConfiguration('other', java, '/extension with spaces/dist/server', folders, '3g'));
  const server = json.mcpServers.requality;
  assert.equal(server.command, java);
  assert.equal(server.args[0], '-Xmx3g');
  assert.deepEqual(server.args.slice(-2), folders);
  assert.ok(server.args.includes('com.unitesk.requality.vscode.mcp.McpMain'));
  const vscode = JSON.parse(mcpConfiguration('vscode', java, '/server', folders, '2g'));
  assert.equal(vscode.servers.requality.type, 'stdio');
  assert.equal(vscode.servers.requality.command, java);
  const toml = mcpConfiguration('codex', java, '/server', folders, '2g');
  assert.ok(toml.startsWith('[mcp_servers.requality]\n'));
  assert.equal(JSON.parse(toml.split('\n')[1].slice('command = '.length)), java);
  assert.deepEqual(JSON.parse(toml.split('\n')[2].slice('args = '.length)).slice(-2), folders);
});

test('configuration never defaults to the agent working directory when no catalog folder is open', () => {
  assert.throws(() => mcpConfiguration('codex', 'java', '/server', [], '2g'), /Open a local folder/);
});
