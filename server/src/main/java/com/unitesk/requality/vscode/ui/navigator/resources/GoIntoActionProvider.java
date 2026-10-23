package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.framelist.GoIntoAction;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchActionConstants;

/**
 * {@code org.eclipse.ui.navigator.resources.GoIntoActionProvider}: "Go Into" for an open project, a folder or a working
 * set: the element becomes the input of the viewer (a new frame of its frame list).
 */
public class GoIntoActionProvider extends CommonActionProvider {
  private GoIntoAction goIntoAction;

  @Override
  public void init(ICommonActionExtensionSite anActionSite) {
    anActionSite.getViewSite().getShell();
    CommonViewer viewer = (CommonViewer) anActionSite.getStructuredViewer();
    goIntoAction = new GoIntoAction(viewer.getFrameList());
  }

  @Override
  public void dispose() {
    goIntoAction.dispose();
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    actionBars.setGlobalActionHandler(IWorkbenchActionConstants.GO_INTO, goIntoAction);
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    menu.appendToGroup("group.new", goIntoAction);
  }

  @Override
  public void updateActionBars() {
    goIntoAction.update();
  }
}
