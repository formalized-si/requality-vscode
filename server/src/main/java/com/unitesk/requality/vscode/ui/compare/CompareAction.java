package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMappingContext;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceTraversal;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IObjectActionDelegate;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/**
 * {@code org.eclipse.compare.internal.CompareAction}: Compare With &gt; Each Other of two or three resources of the same
 * kind (files, or folders that are compared as structures). The compare editor is opened on the resources selected.
 */
public class CompareAction extends BaseCompareAction implements IObjectActionDelegate {
  protected ResourceCompareInput fInput;
  protected Shell fShell;
  protected boolean showSelectAncestorDialog = true;

  @Override
  protected void run(ISelection selection) {
    if (fInput != null) {
      // Pass the shell so setSelection can prompt the user for which resource should be the ancestor
      Shell shell = fShell != null ? fShell : Display.getDefault().getActiveShell();
      boolean ok = fInput.setSelection(selection, shell, showSelectAncestorDialog);
      if (!ok)
        return;
      fInput.initializeCompareConfiguration();
      CompareUI.openCompareEditor(fInput);
      fInput = null; // don't reuse this input!
    }
  }

  @Override
  protected boolean isEnabled(ISelection selection) {
    if (fInput == null) {
      CompareConfiguration cc = new CompareConfiguration();
      // buffered merge mode: don't ask for confirmation when switching between modified resources
      cc.setProperty(CompareEditor.CONFIRM_SAVE_PROPERTY, Boolean.FALSE);
      fInput = new ResourceCompareInput(cc);
    }
    return fInput.isEnabled(selection);
  }

  @Override
  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    // VSCODE: the popup menu creates its actions without a part, the shell is then the active one
    fShell = targetPart == null || targetPart.getSite() == null ? null : targetPart.getSite().getShell();
  }

  /** The file compared for a selected element: the file itself, or the file of its resource mapping. */
  public static IFile file(Object o) {
    IFile f = Adapters.adapt(o, IFile.class);
    if (f != null)
      return f;
    ResourceMapping m = Adapters.adapt(o, ResourceMapping.class);
    if (m == null)
      return null;
    try {
      for (ResourceTraversal t : m.getTraversals(ResourceMappingContext.LOCAL_CONTEXT, new NullProgressMonitor()))
        for (IResource res : t.getResources())
          if (res instanceof IFile)
            return (IFile) res;
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    return null;
  }
}
