package com.unitesk.requality.vscode.ui.team.actions;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.team.RepositoryProvider;
import com.unitesk.requality.vscode.ui.team.wizards.ConfigureProjectWizard;
import com.unitesk.requality.vscode.ui.workbench.IObjectActionDelegate;

/**
 * {@code org.eclipse.team.internal.ui.actions.ConfigureProjectAction}: Team &gt; Share Project... The selected projects
 * that are accessible and not shared yet are shared with a repository type chosen in the wizard.
 */
public class ConfigureProjectAction extends TeamAction implements IObjectActionDelegate {

  @Override
  protected void execute(IAction action) throws InvocationTargetException, InterruptedException {
    if (!isEnabled())
      return;
    try {
      ConfigureProjectWizard.shareProjects(getShell(), getSelectedProjects());
    } catch (RuntimeException e) {
      throw new InvocationTargetException(e);
    }
  }

  /** The selected projects are accessible and none of them is shared yet. */
  @Override
  public boolean isEnabled() {
    IProject[] selectedProjects = getSelectedProjects();
    for (IProject project : selectedProjects) {
      if (!project.isAccessible())
        return false;
      if (RepositoryProvider.isShared(project))
        return false;
    }
    return selectedProjects.length > 0;
  }
}
