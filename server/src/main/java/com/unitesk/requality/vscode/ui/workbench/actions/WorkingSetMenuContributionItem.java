package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * {@code org.eclipse.ui.internal.WorkingSetMenuContributionItem}: a menu entry of the most recently used working sets,
 * numbered from 1 (a radio entry, checked when it is the active working set); choosing it makes the working set the
 * active one.
 * VSCODE: the entry is an action item (the menus of the explorer are listed by their actions, not filled into SWT menus).
 */
public class WorkingSetMenuContributionItem extends ActionContributionItem {
  /** The id of the menu contribution of the number. */
  public static String getId(int id) {
    return WorkingSetMenuContributionItem.class.getName() + "." + id;
  }

  /**
   * @param id          the number of the entry (1 for the most recent)
   * @param actionGroup the action group the entry is created in
   * @param workingSet  the working set of the entry
   */
  public WorkingSetMenuContributionItem(int id, WorkingSetFilterActionGroup actionGroup, IWorkingSet workingSet) {
    super(new Action("&" + id + " " + workingSet.getLabel(), IAction.AS_RADIO_BUTTON) {
      @Override
      public void run() {
        actionGroup.setWorkingSet(workingSet);
        Workbench.INSTANCE.getWorkingSetManager().addRecentWorkingSet(workingSet);
      }
    });
    Assert.isNotNull(actionGroup);
    Assert.isNotNull(workingSet);
    getAction().setChecked(workingSet.equals(actionGroup.getWorkingSet()));
  }

  @Override
  public boolean isDynamic() {
    return true;
  }
}
