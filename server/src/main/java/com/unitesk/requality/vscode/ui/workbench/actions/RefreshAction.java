package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.RefreshAction}: refreshes the selected resources from the file system in a
 * background job ("Refreshing").
 */
public class RefreshAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.RefreshAction";

  public RefreshAction(Supplier<Shell> shell) {
    super("Re&fresh", shell);
    setToolTipText("Refresh");
    setId(ID);
    setActionDefinitionId(ActionFactory.REFRESH.getCommandId());
  }

  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    if (!getSelectedNonResources().isEmpty())
      return false;
    for (IResource r : getSelectedResources())
      if (r.getType() == IResource.PROJECT && !((com.unitesk.requality.vscode.ui.resources.IProject) r).isOpen())
        return false;
    return true;
  }

  @Override
  public void run() {
    java.util.List<IResource> resources = getSelectedResources();
    Job job = new Job("Refreshing") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Refreshing", resources.size());
        try {
          for (IResource r : resources) {
            if (monitor.isCanceled())
              return Status.CANCEL_STATUS;
            try {
              r.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            } catch (CoreException e) {
              return e.getStatus();
            }
            monitor.worked(1);
          }
          return Status.OK_STATUS;
        } finally {
          monitor.done();
        }
      }
    };
    job.setUser(true);
    job.schedule();
  }
}
