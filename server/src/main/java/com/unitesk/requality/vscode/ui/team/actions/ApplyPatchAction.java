package com.unitesk.requality.vscode.ui.team.actions;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.patch.ApplyPatchOperation;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMappingContext;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceTraversal;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.workbench.IObjectActionDelegate;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.team.internal.ui.actions.ApplyPatchAction}: Team &gt; Apply Patch... The selected file is the patch when
 * it contains one; otherwise the wizard opens with its input page and the selected resource as the target.
 */
public class ApplyPatchAction extends ActionDelegate implements IObjectActionDelegate {
  private IResource resource;
  private IWorkbenchPart part;

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    resource = sel instanceof IStructuredSelection ? resourceOf(((IStructuredSelection) sel).getFirstElement()) : null;
    action.setEnabled(true);
  }

  @Override
  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    part = targetPart;
  }

  @Override
  public void run(IAction action) {
    IResource r = resource;
    boolean isPatch = false;
    if (r instanceof IFile) {
      try {
        isPatch = ApplyPatchOperation.isPatch((IFile) r);
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    ApplyPatchOperation op = isPatch
        ? new ApplyPatchOperation(part, (IFile) r, null, new CompareConfiguration())
        : new ApplyPatchOperation(part, r);
    op.run();
  }

  /** The resource of a selected element: the resource itself, or the first resource of its mapping. */
  static IResource resourceOf(Object o) {
    IResource r = Adapters.adapt(o, IResource.class);
    if (r != null)
      return r;
    ResourceMapping m = Adapters.adapt(o, ResourceMapping.class);
    if (m == null)
      return null;
    try {
      for (ResourceTraversal t : m.getTraversals(ResourceMappingContext.LOCAL_CONTEXT, new NullProgressMonitor()))
        for (IResource res : t.getResources())
          return res;
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    return null;
  }
}
