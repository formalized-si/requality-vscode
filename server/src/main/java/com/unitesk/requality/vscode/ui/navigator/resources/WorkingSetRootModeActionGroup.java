package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;
import com.unitesk.requality.vscode.ui.navigator.IExtensionStateModel;
import com.unitesk.requality.vscode.ui.navigator.workingsets.WorkingSetsContentProvider;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchActionConstants;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionGroup;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.WorkingSetRootModeActionGroup}: "Top Level Elements" in
 * the view menu: Projects or Working Sets (radio items).
 */
public class WorkingSetRootModeActionGroup extends ActionGroup {
  private IExtensionStateModel stateModel;
  private final StructuredViewer structuredViewer;
  private boolean hasContributedToViewMenu = false;
  private IAction workingSetsAction = null;
  private IAction projectsAction = null;
  private IAction[] actions;
  private int currentRadioSelection;

  private class TopLevelContentAction extends Action {
    private final boolean groupWorkingSets;
    private final int index;

    TopLevelContentAction(boolean toGroupWorkingSets, int index) {
      super("", AS_RADIO_BUTTON);
      groupWorkingSets = toGroupWorkingSets;
      this.index = index;
    }

    @Override
    public void run() {
      // the radio item of the view menu (WorkingSetRootModeActionGroup.addActions): a selected item does nothing
      if (currentRadioSelection == index)
        return;
      if (stateModel.getBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS) != groupWorkingSets) {
        stateModel.setBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS, groupWorkingSets);
        structuredViewer.refresh();
      }
      actions[currentRadioSelection].setChecked(false);
      currentRadioSelection = index;
      actions[currentRadioSelection].setChecked(true);
    }
  }

  public WorkingSetRootModeActionGroup(StructuredViewer aStructuredViewer, IExtensionStateModel aStateModel) {
    structuredViewer = aStructuredViewer;
    stateModel = aStateModel;
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    if (hasContributedToViewMenu)
      return;
    IMenuManager topLevelSubMenu = new MenuManager("&Top Level Elements");
    addActions(topLevelSubMenu);
    IMenuManager viewMenu = actionBars.getMenuManager();
    if (viewMenu.find(IWorkbenchActionConstants.MB_ADDITIONS) == null)
      viewMenu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    viewMenu.insertBefore(IWorkbenchActionConstants.MB_ADDITIONS, topLevelSubMenu);
    hasContributedToViewMenu = true;
  }

  protected void addActions(IMenuManager viewMenu) {
    if (actions == null)
      actions = createActions();
    viewMenu.add(new Separator());
    for (int i = 0; i < actions.length; i++) {
      actions[i].setChecked(currentRadioSelection == i);
      viewMenu.add(actions[i]);
    }
  }

  private IAction[] createActions() {
    projectsAction = new TopLevelContentAction(false, 0);
    projectsAction.setText("Projects");
    workingSetsAction = new TopLevelContentAction(true, 1);
    workingSetsAction.setText("Working Sets");
    return new IAction[] { projectsAction, workingSetsAction };
  }

  public void setShowTopLevelWorkingSets(boolean showTopLevelWorkingSets) {
    if (actions == null)
      actions = createActions();
    currentRadioSelection = showTopLevelWorkingSets ? 1 : 0;
    workingSetsAction.setChecked(showTopLevelWorkingSets);
    projectsAction.setChecked(!showTopLevelWorkingSets);
    if (stateModel != null)
      stateModel.setBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS, showTopLevelWorkingSets);
  }

  public void setStateModel(IExtensionStateModel sStateModel) {
    stateModel = sStateModel;
  }
}
