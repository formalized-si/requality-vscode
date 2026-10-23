package com.unitesk.requality.vscode.ui.workbench.model;

import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/** {@code org.eclipse.ui.model.BaseWorkbenchContentProvider} with the resource adapters of the IDE. */
public class BaseWorkbenchContentProvider implements ITreeContentProvider {
  protected IWorkbenchAdapter getAdapter(Object element) {
    return (IWorkbenchAdapter) com.unitesk.requality.vscode.ui.runtime.Platform.getAdapterManager().getAdapter(element,
        IWorkbenchAdapter.class);
  }

  @Override
  public Object[] getChildren(Object element) {
    IWorkbenchAdapter a = getAdapter(element);
    if (a != null)
      return a.getChildren(element);
    if (element instanceof IWorkspace)
      return ((IWorkspace) element).getRoot().getProjects();
    if (element instanceof IContainer) {
      IContainer c = (IContainer) element;
      if (!c.isAccessible())
        return new Object[0];
      try {
        return c.members();
      } catch (CoreException e) {
        return new Object[0];
      }
    }
    return new Object[0];
  }

  @Override
  public Object[] getElements(Object element) {
    return getChildren(element);
  }

  @Override
  public Object getParent(Object element) {
    IWorkbenchAdapter a = getAdapter(element);
    if (a != null)
      return a.getParent(element);
    if (element instanceof IResource)
      return ((IResource) element).getParent();
    return null;
  }

  @Override
  public boolean hasChildren(Object element) {
    return getChildren(element).length > 0;
  }

  @Override
  public void dispose() {
  }

  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
  }
}
