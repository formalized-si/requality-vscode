package com.unitesk.requality.vscode.ui.workbench.actions;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchActionConstants;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.CompoundContributionItem;

/**
 * {@code org.eclipse.ui.actions.WorkingSetFilterActionGroup}: the working set actions of a view (Select, Deselect, Edit
 * the active working set, and the most recently used working sets). The active working set is reported to the
 * updater, which filters the view.
 * The window working set of the active page is the first of the recently used entries. No working set is active by
 * default (the group does not allow the window working set by default).
 */
public class WorkingSetFilterActionGroup extends ActionGroup {
  private static final String TAG_WORKING_SET_NAME = "workingSetName";
  /** Indicates if the working set was changed (the property of the updater's event). */
  public static final String CHANGE_WORKING_SET = "changeWorkingSet";

  private static final String START_SEPARATOR_ID = "workingSetGroupStartSeparator";
  private static final String SEPARATOR_ID = "workingSetGroupSeparator";
  private static final String WORKING_SET_ACTION_GROUP = "workingSetActionGroup";

  private IWorkingSet workingSet = null;
  private final ClearWorkingSetAction clearWorkingSetAction;
  private final SelectWorkingSetAction selectWorkingSetAction;
  private final EditWorkingSetAction editWorkingSetAction;
  private final IPropertyChangeListener workingSetUpdater;
  private IMenuManager menuManager;
  private final CompoundContributionItem mruList;

  /**
   * Creates a new instance of the receiver.
   *
   * @param shell             the shell the dialogs and wizards open on
   * @param workingSetUpdater property change listener notified when a working set is set
   */
  public WorkingSetFilterActionGroup(Shell shell, IPropertyChangeListener workingSetUpdater) {
    Assert.isNotNull(shell);
    this.workingSetUpdater = workingSetUpdater;
    clearWorkingSetAction = new ClearWorkingSetAction(this);
    selectWorkingSetAction = new SelectWorkingSetAction(this, shell);
    editWorkingSetAction = new EditWorkingSetAction(this, shell);
    mruList = new CompoundContributionItem() {
      @Override
      protected IContributionItem[] getContributionItems() {
        IWorkingSet[] workingSets = Workbench.INSTANCE.getWorkingSetManager().getRecentWorkingSets();
        List<IContributionItem> items = new ArrayList<>(workingSets.length + 1);
        List<IWorkingSet> sortedWorkingSets = new ArrayList<>(Arrays.asList(workingSets));
        Collator collator = Collator.getInstance();
        sortedWorkingSets.sort((a, b) -> {
          int r = collator.compare(a.getLabel(), b.getLabel());
          return r == 0 ? a.getName().compareTo(b.getName()) : r;
        });
        int mruMenuCount = 0;
        IWorkbenchPage page = activePage();
        // the window working set comes first
        if (page != null && page.getAggregateWorkingSet() != null) {
          items.add(new WorkingSetMenuContributionItem(++mruMenuCount, WorkingSetFilterActionGroup.this,
              page.getAggregateWorkingSet()));
        }
        for (IWorkingSet workingSet : sortedWorkingSets) {
          if (workingSet != null && !workingSet.isAggregateWorkingSet()) {
            items.add(new WorkingSetMenuContributionItem(++mruMenuCount, WorkingSetFilterActionGroup.this, workingSet));
          }
        }
        return items.toArray(new IContributionItem[items.size()]);
      }
    };
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    menuManager = actionBars.getMenuManager();
    if (menuManager.find(IWorkbenchActionConstants.MB_ADDITIONS) != null) {
      menuManager.insertAfter(IWorkbenchActionConstants.MB_ADDITIONS, new Separator(WORKING_SET_ACTION_GROUP));
    } else {
      menuManager.add(new Separator(WORKING_SET_ACTION_GROUP));
    }
    menuManager.appendToGroup(WORKING_SET_ACTION_GROUP, selectWorkingSetAction);
    menuManager.appendToGroup(WORKING_SET_ACTION_GROUP, clearWorkingSetAction);
    menuManager.appendToGroup(WORKING_SET_ACTION_GROUP, editWorkingSetAction);
    menuManager.appendToGroup(WORKING_SET_ACTION_GROUP, new Separator(START_SEPARATOR_ID));
    menuManager.appendToGroup(WORKING_SET_ACTION_GROUP, mruList);
    menuManager.appendToGroup(WORKING_SET_ACTION_GROUP, new Separator(SEPARATOR_ID));
  }

  @Override
  public void fillContextMenu(IMenuManager menuManager) {
    menuManager.add(selectWorkingSetAction);
    menuManager.add(clearWorkingSetAction);
    menuManager.add(editWorkingSetAction);
    menuManager.add(new Separator());
    menuManager.add(mruList);
    menuManager.add(new Separator(SEPARATOR_ID));
  }

  /** The active page of the workbench window, or null. */
  private static IWorkbenchPage activePage() {
    IWorkbenchWindow window = Workbench.INSTANCE.getActiveWorkbenchWindow();
    return window == null ? null : window.getActivePage();
  }

  /** The working set which is currently active, or null. */
  public IWorkingSet getWorkingSet() {
    return workingSet;
  }

  /** Sets the active working set (null: none) and updates the actions and the updater. */
  public void setWorkingSet(IWorkingSet newWorkingSet) {
    IWorkingSet oldWorkingSet = workingSet;
    workingSet = newWorkingSet;
    // Update action
    clearWorkingSetAction.setEnabled(newWorkingSet != null);
    editWorkingSetAction.setEnabled(newWorkingSet != null && newWorkingSet.isEditable());
    firePropertyChange(newWorkingSet, oldWorkingSet);
  }

  /** Notifies the updater (the view) that the active working set changed. */
  private void firePropertyChange(IWorkingSet newWorkingSet, IWorkingSet oldWorkingSet) {
    if (workingSetUpdater != null && newWorkingSet != oldWorkingSet) {
      workingSetUpdater.propertyChange(new PropertyChangeEvent(this, CHANGE_WORKING_SET, oldWorkingSet, newWorkingSet));
    }
  }

  /** Saves the active working set (by name) in the memento. */
  public void saveState(IMemento memento) {
    if (workingSet != null) {
      memento.putString(TAG_WORKING_SET_NAME, workingSet.getName());
    } else {
      memento.putString(TAG_WORKING_SET_NAME, "");
    }
  }

  /**
   * Restores the active working set from the memento. Does not refresh the viewer.
   */
  public void restoreState(IMemento memento) {
    String workingSetName = memento.getString(TAG_WORKING_SET_NAME);
    if (workingSetName != null && workingSetName.length() > 0) {
      setWorkingSet(Workbench.INSTANCE.getWorkingSetManager().getWorkingSet(workingSetName));
    }
  }

  /** Disposes the group. */
  @Override
  public void dispose() {
    super.dispose();
  }
}
