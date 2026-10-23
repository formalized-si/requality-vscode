package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IPartListener}. */
public interface IPartListener {
  void partActivated(IWorkbenchPart part);

  void partBroughtToTop(IWorkbenchPart part);

  void partClosed(IWorkbenchPart part);

  void partDeactivated(IWorkbenchPart part);

  void partOpened(IWorkbenchPart part);
}
