import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { App, command } from '../app';
import { findJava } from '../server/java';
import { McpClient, mcpConfiguration } from '../server/mcpConfiguration';

/** Prepare a reviewable config without overwriting the agent's existing servers. */
export function activateMcp(app: App): void {
  app.context.subscriptions.push(command(app, 'requality.configureMcp', async () => {
    const folders = (vscode.workspace.workspaceFolders ?? []).filter(f => f.uri.scheme === 'file').map(f => f.uri.fsPath);
    if (!folders.length) {
      throw new Error('Open a local folder with Requality catalogs first.');
    }
    const choice = await vscode.window.showQuickPick<{ label: string; description: string; client: McpClient }>([
      { label: 'Codex', description: 'Add to ~/.codex/config.toml', client: 'codex' },
      { label: 'VS Code', description: 'Merge into .vscode/mcp.json', client: 'vscode' },
      { label: 'Claude Code, Claude Desktop or Cursor', description: 'Merge the mcpServers entry into your agent configuration', client: 'other' },
    ], { title: 'Connect Requality to an AI agent', placeHolder: 'Choose your agent' });
    if (!choice) {
      return;
    }
    const server = app.context.asAbsolutePath(path.join('dist', 'server'));
    if (!fs.existsSync(path.join(server, 'lib', 'requality-server.jar'))) {
      throw new Error('Requality server is missing. Build it with "npm run build:server".');
    }
    const settings = vscode.workspace.getConfiguration('requality');
    const java = await findJava(settings.get<string>('java.home'), app.context.asAbsolutePath('jre'));
    const content = mcpConfiguration(choice.client, java.executable, server, folders,
      settings.get<string>('server.maxHeap') || '2g');
    const language = choice.client === 'codex'
      ? ((await vscode.languages.getLanguages()).includes('toml') ? 'toml' : 'plaintext')
      : 'json';
    const doc = await vscode.workspace.openTextDocument({ language, content });
    await vscode.window.showTextDocument(doc);
    const action = await vscode.window.showInformationMessage(
      `${choice.description}. Close the catalog in Requality before using it from the agent.`, 'Copy Configuration');
    if (action) {
      await vscode.env.clipboard.writeText(content);
    }
  }));
}
