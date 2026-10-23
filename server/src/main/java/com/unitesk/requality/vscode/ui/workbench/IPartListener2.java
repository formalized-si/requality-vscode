package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IPartListener2}: the part listener that gets the part references. */
public interface IPartListener2 {
  void partActivated(IWorkbenchPartReference partRef);

  void partBroughtToTop(IWorkbenchPartReference partRef);

  void partClosed(IWorkbenchPartReference partRef);

  void partDeactivated(IWorkbenchPartReference partRef);

  void partOpened(IWorkbenchPartReference partRef);

  void partHidden(IWorkbenchPartReference partRef);

  void partVisible(IWorkbenchPartReference partRef);

  void partInputChanged(IWorkbenchPartReference partRef);
}
