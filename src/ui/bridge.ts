import * as vscode from 'vscode';

/** Messages posted by the page bridge (server/src/main/web/vscode-bridge.js) through a Browser webview. */
export interface BridgeMessage {
  type: 'alert' | 'open-external';
  text?: string;
  url?: string;
}

/** Handles messages from the page bridge: alerts (webviews block modal dialogs) and external links. */
export function handleBridgeMessage(m: BridgeMessage): void {
  if (m.type === 'alert' && typeof m.text === 'string') {
    void vscode.window.showWarningMessage(m.text);
  } else if (m.type === 'open-external' && typeof m.url === 'string') {
    void vscode.env.openExternal(vscode.Uri.parse(m.url));
  }
}
