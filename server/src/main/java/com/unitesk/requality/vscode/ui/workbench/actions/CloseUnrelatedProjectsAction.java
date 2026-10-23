package com.unitesk.requality.vscode.ui.workbench.actions;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialogWithToggle;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.CloseUnrelatedProjectsAction}: after a confirmation, closes the open projects that
 * neither reference nor are referenced by the selected projects.
 */
public class CloseUnrelatedProjectsAction extends CloseResourceAction {
  public static final String ID = "org.eclipse.ui.CloseUnrelatedProjectsAction";

  public CloseUnrelatedProjectsAction(Supplier<Shell> shell) {
    super("Close U&nrelated Projects", shell);
    setToolTipText("Close Unrelated Projects");
    setId(ID);
    setActionDefinitionId(ActionFactory.CLOSE_UNRELATED_PROJECTS.getCommandId());
  }

  private static void related(IProject p, Set<IProject> into) {
    if (!into.add(p))
      return;
    try {
      for (IProject r : p.getReferencedProjects())
        related(r, into);
    } catch (CoreException e) {
      // not open
    }
    for (IProject other : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      try {
        if (other.exists() && other.isOpen())
          for (IProject r : other.getReferencedProjects())
            if (r.equals(p))
              related(other, into);
      } catch (CoreException e) {
        // not open
      }
  }

  @Override
  protected List<IProject> getProjectsToClose() {
    Set<IProject> keep = new LinkedHashSet<>();
    for (IProject p : getSelectedProjects())
      related(p, keep);
    List<IProject> r = new ArrayList<>();
    for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      if (p.exists() && p.isOpen() && !keep.contains(p))
        r.add(p);
    return r;
  }

  @Override
  protected boolean updateSelection(com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection selection) {
    return !getSelectedProjects().isEmpty() && selectionIsOfType(com.unitesk.requality.vscode.ui.resources.IResource.PROJECT);
  }

  @Override
  public void run() {
    if (promptForConfirmation())
      super.run();
  }

  /**
   * {@code CloseUnrelatedProjectsAction.promptForConfirmation}: with the preference "Always close unrelated projects
   * without prompt" nothing is asked; else a confirmation with the toggle of that preference, which the answer sets.
   */
  private boolean promptForConfirmation() {
    IPreferenceStore store = IDEInternalPreferences.getStore();
    if (store.getBoolean(IDEInternalPreferences.CLOSE_UNRELATED_PROJECTS))
      return true;
    List<IProject> selection = getSelectedProjects();
    int selectionSize = selection.size();
    if (selectionSize == 0)
      return true;
    String message;
    if (selectionSize == 1)
      message = MessageFormat.format("Close all projects that are not related to ''{0}''?", selection.get(0).getName());
    else
      message = MessageFormat.format("Close all projects that are not related to these {0} projects?", selectionSize);
    MessageDialogWithToggle dialog = MessageDialogWithToggle.openOkCancelConfirm(getShell(), "Close Unrelated Project",
        message, "&Always close without prompt", false, null, null);
    if (dialog.getReturnCode() != IDialogConstants.OK_ID)
      return false;
    store.setValue(IDEInternalPreferences.CLOSE_UNRELATED_PROJECTS, dialog.getToggleState());
    return true;
  }
}
