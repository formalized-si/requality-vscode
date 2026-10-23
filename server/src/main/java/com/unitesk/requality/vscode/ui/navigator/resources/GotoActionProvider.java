package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchActionConstants;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.GotoActionProvider}: "Go to Resource" (the global action
 * {@link IWorkbenchActionConstants#GO_TO_RESOURCE}).
 */
public class GotoActionProvider extends CommonActionProvider {
  private GotoResourceAction gotoAction;

  @Override
  public void init(ICommonActionExtensionSite anActionSite) {
    super.init(anActionSite);
    gotoAction = new GotoResourceAction(anActionSite.getViewSite().getShell(),
        (CommonViewer) anActionSite.getStructuredViewer());
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    actionBars.setGlobalActionHandler(IWorkbenchActionConstants.GO_TO_RESOURCE, gotoAction);
    updateActionBars();
  }
}
