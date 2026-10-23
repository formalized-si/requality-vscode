package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.action.IAction;

/**
 * {@code org.eclipse.ui.IObjectActionDelegate}: an action of a popupMenus {@code objectContribution} that is told the
 * active part before it is selected and run.
 */
public interface IObjectActionDelegate extends IActionDelegate {
  void setActivePart(IAction action, IWorkbenchPart targetPart);
}
