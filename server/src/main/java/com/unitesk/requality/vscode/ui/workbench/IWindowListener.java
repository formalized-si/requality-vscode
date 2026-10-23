package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IWindowListener}: the listener of the workbench windows. */
public interface IWindowListener {
  void windowActivated(IWorkbenchWindow window);

  void windowDeactivated(IWorkbenchWindow window);

  void windowClosed(IWorkbenchWindow window);

  void windowOpened(IWorkbenchWindow window);
}
