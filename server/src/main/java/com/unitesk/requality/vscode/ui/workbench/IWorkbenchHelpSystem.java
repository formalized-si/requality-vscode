package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.help.IWorkbenchHelpSystem}: shows the Requality manual. */
public interface IWorkbenchHelpSystem {
  void displayHelp();

  void displayHelp(String contextId);

  void displayHelpResource(String href);

  void setHelp(com.unitesk.requality.vscode.ui.swt.widgets.Control control, String contextId);
}
