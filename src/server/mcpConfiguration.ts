import * as path from 'path';

export type McpClient = 'codex' | 'vscode' | 'other';

/** Direct Java invocation keeps paths as arguments on every platform, including Windows. */
export function mcpConfiguration(client: McpClient, java: string, server: string, folders: string[], maxHeap: string): string {
  if (!folders.length) {
    throw new Error('Open a local folder with Requality catalogs first.');
  }
  const config = {
    command: java,
    args: [
      `-Xmx${maxHeap}`, '-Dfile.encoding=UTF-8', '-Djava.awt.headless=true',
      '--add-opens', 'java.base/java.lang=ALL-UNNAMED', '-cp', path.join(server, 'lib', '*'),
      'com.unitesk.requality.vscode.mcp.McpMain', '--resources', path.join(server, 'resources'),
      ...folders,
    ],
  };
  if (client === 'codex') {
    return '[mcp_servers.requality]\n'
      + `command = ${JSON.stringify(config.command)}\n`
      + `args = ${JSON.stringify(config.args)}\n`;
  }
  return JSON.stringify(client === 'vscode'
    ? { servers: { requality: { type: 'stdio', ...config } } }
    : { mcpServers: { requality: config } }, null, 2) + '\n';
}
