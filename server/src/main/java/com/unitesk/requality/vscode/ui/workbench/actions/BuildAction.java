package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.BuildManager;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IncrementalProjectBuilder;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.BuildAction} ("Build Project"): builds the selected open projects in a background
 * job. The navigator offers it only when the workspace does not build automatically.
 */
public class BuildAction extends SelectionListenerAction {
  public static final String ID_BUILD = "org.eclipse.ui.BuildAction";
  private final int buildType;

  public BuildAction(Supplier<Shell> shell, int type) {
    super(type == IncrementalProjectBuilder.FULL_BUILD ? "Re&build Project" : "&Build Project", shell);
    this.buildType = type;
    setToolTipText(type == IncrementalProjectBuilder.FULL_BUILD ? "Rebuild Project" : "Build Project");
    setId(ID_BUILD);
    setActionDefinitionId(ActionFactory.BUILD_PROJECT.getCommandId());
  }

  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    if (!selectionIsOfType(IResource.PROJECT) || getSelectedProjects().isEmpty())
      return false;
    for (IProject p : getSelectedProjects())
      if (!p.isOpen())
        return false;
    return true;
  }

  @Override
  public void run() {
    List<IProject> projects = getSelectedProjects();
    schedule(projects, buildType);
  }

  /** The build job of the given projects (all open projects when null). */
  public static Job schedule(List<IProject> projects, int kind) {
    Job job = new Job("Building") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        try {
          BuildManager.build(projects, kind, monitor);
          return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
        } catch (CoreException e) {
          return e.getStatus();
        }
      }
    };
    job.setUser(true);
    job.schedule();
    return job;
  }
}
