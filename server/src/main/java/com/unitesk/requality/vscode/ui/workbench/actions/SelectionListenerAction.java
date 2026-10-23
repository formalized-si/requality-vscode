package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.SelectionListenerAction}: an action enabled by {@link #updateSelection} for the
 * selection it was last given, with the selected resources (elements that are or adapt to IResource).
 */
public abstract class SelectionListenerAction extends Action {
  private IStructuredSelection selection = StructuredSelection.EMPTY;
  protected final Supplier<Shell> shell;

  protected SelectionListenerAction(String text, Supplier<Shell> shell) {
    super(text);
    this.shell = shell;
  }

  public final void selectionChanged(IStructuredSelection s) {
    selection = s == null ? StructuredSelection.EMPTY : s;
    setEnabled(updateSelection(selection));
  }

  protected boolean updateSelection(IStructuredSelection s) {
    return true;
  }

  public IStructuredSelection getStructuredSelection() {
    return selection;
  }

  static IResource resourceOf(Object o) {
    if (o instanceof IResource)
      return (IResource) o;
    if (o instanceof IAdaptable)
      return ((IAdaptable) o).getAdapter(IResource.class);
    return null;
  }

  /** The selected elements that are resources. */
  protected List<IResource> getSelectedResources() {
    List<IResource> r = new ArrayList<>();
    for (Object o : selection.toList()) {
      IResource res = resourceOf(o);
      if (res != null)
        r.add(res);
    }
    return r;
  }

  /** The selected elements that are not resources. */
  protected List<Object> getSelectedNonResources() {
    List<Object> r = new ArrayList<>();
    for (Object o : selection.toList())
      if (resourceOf(o) == null)
        r.add(o);
    return r;
  }

  /** True when every selected element is a resource of one of the types (IResource.PROJECT | FOLDER | FILE). */
  protected boolean selectionIsOfType(int typeMask) {
    if (!getSelectedNonResources().isEmpty())
      return false;
    for (IResource r : getSelectedResources())
      if ((r.getType() & typeMask) == 0)
        return false;
    return true;
  }

  /** The selected projects. */
  protected List<IProject> getSelectedProjects() {
    List<IProject> r = new ArrayList<>();
    for (IResource res : getSelectedResources())
      if (res instanceof IProject)
        r.add((IProject) res);
    return r;
  }

  protected Shell getShell() {
    return shell == null ? null : shell.get();
  }
}
