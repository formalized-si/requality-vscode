import * as vscode from 'vscode';

/** The VS Code view of the Requality Explorer as the Eclipse view: the SWT tree of the part, drawn by the server. */
export const ECLIPSE_EXPLORER_VIEW = 'requality.explorerView';
/** The VS Code view of the Requality Explorer as a native VS Code tree. */
export const NATIVE_EXPLORER_VIEW = 'requality.explorer';

/**
 * How the Requality Explorer is shown (setting requality.explorer.view): "eclipse", the view of the plug-in with its
 * tree, popup menu, drag and drop and key bindings; "native", a VS Code tree whose items the server gives.
 */
export function explorerViewMode(): 'eclipse' | 'native' {
  return vscode.workspace.getConfiguration('requality').get<string>('explorer.view') === 'native' ? 'native' : 'eclipse';
}

/** The VS Code view that shows the Requality Explorer. */
export function explorerViewId(): string {
  return explorerViewMode() === 'native' ? NATIVE_EXPLORER_VIEW : ECLIPSE_EXPLORER_VIEW;
}
