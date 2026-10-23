package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.dialogs.ElementTreeSelectionDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ISelectionStatusValidator;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.ui.dialogs.ContainerSelectionDialog}: a tree of the workspace folders and projects, one of
 * which is selected. {@link #getResult()} returns the {@link IPath}s of the selected containers. The dialog
 * selects existing containers only (the Eclipse option to type a new container name is not offered).
 */
public class ContainerSelectionDialog extends ElementTreeSelectionDialog {

  public ContainerSelectionDialog(Shell parent, IContainer rootContainer, boolean allowNewContainerName, String msg) {
    super(parent, WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider(), new ContainerContentProvider());
    setTitle("Folder Selection");
    setMessage(msg);
    setInput(rootContainer);
    setAllowMultiple(false);
    setValidator(new ISelectionStatusValidator() {
      @Override
      public IStatus validate(Object[] selection) {
        if (selection != null && selection.length == 1 && selection[0] instanceof IContainer)
          return Status.OK_STATUS;
        return new Status(IStatus.ERROR, "com.unitesk.requality", "Select a folder.");
      }
    });
  }

  /** The full paths ({@link IPath}) of the selected containers, or null if the dialog was not confirmed. */
  @Override
  public Object[] getResult() {
    Object[] selected = super.getResult();
    if (selected == null)
      return null;
    Object[] paths = new Object[selected.length];
    for (int i = 0; i < selected.length; i++)
      paths[i] = ((IContainer) selected[i]).getFullPath();
    return paths;
  }

  private static final class ContainerContentProvider implements ITreeContentProvider {
    @Override
    public void dispose() {
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }

    @Override
    public Object[] getElements(Object inputElement) {
      return getChildren(inputElement);
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      if (!(parentElement instanceof IContainer))
        return new Object[0];
      IContainer container = (IContainer) parentElement;
      java.util.List<IResource> result = new java.util.ArrayList<>();
      try {
        if (container.isAccessible())
          for (IResource r : container.members())
            if (r instanceof IContainer && !r.isHidden() && r.isAccessible())
              result.add(r);
      } catch (CoreException e) {
        e.printStackTrace();
      }
      return result.toArray();
    }

    @Override
    public Object getParent(Object element) {
      return element instanceof IResource ? ((IResource) element).getParent() : null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return getChildren(element).length > 0;
    }
  }
}
