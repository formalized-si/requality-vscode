package com.unitesk.requality.vscode.ui.team.actions;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMappingContext;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceTraversal;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.team.internal.ui.actions.TeamAction}: the abstract superclass of the Team actions. It maps the
 * selection to its resources (a resource mapping, such as a Requality node, contributes the resources of its
 * traversals), to its projects and to its shell; {@link #run(IAction)} executes the action and reports its errors.
 */
public abstract class TeamAction extends ActionDelegate {
  /** {@code TeamAction.PROGRESS_DIALOG}: the progress is shown in a dialog. */
  public static final int PROGRESS_DIALOG = 1;
  /** {@code TeamAction.PROGRESS_BUSYCURSOR}: the progress is shown with a busy cursor. */
  public static final int PROGRESS_BUSYCURSOR = 2;

  private IStructuredSelection selection;
  private Shell shell;
  private IWorkbenchPart targetPart;

  /** {@code TeamAction.getSelectedAdaptables}: the elements of the selection that adapt to the class. */
  public static <T> T[] getSelectedAdaptables(ISelection selection, Class<T> c) {
    List<T> result = new ArrayList<>();
    if (selection instanceof IStructuredSelection && !selection.isEmpty())
      for (Object o : ((IStructuredSelection) selection).toList()) {
        T adapter = getAdapter(o, c);
        if (c.isInstance(adapter))
          result.add(adapter);
      }
    @SuppressWarnings("unchecked")
    T[] array = (T[]) Array.newInstance(c, result.size());
    return result.toArray(array);
  }

  /** {@code TeamAction.getAdapter}: the object if it is of the class, else its adapter of the class, or null. */
  public static <T> T getAdapter(Object adaptable, Class<T> c) {
    return Adapters.adapt(adaptable, c);
  }

  /** {@code TeamAction.getSelectedResources}: the resources of the selection (and of the resource mappings selected). */
  protected IResource[] getSelectedResources() {
    return contributedResources(getSelection().toList());
  }

  /**
   * {@code Utils.getContributedResources}: the resource of each element (the element itself, its resource adapter, or
   * the file a Requality node adapts to), else all the resources of the traversals of its resource mapping.
   */
  static IResource[] contributedResources(List<?> elements) {
    List<IResource> result = new ArrayList<>();
    for (Object o : elements) {
      IResource resource = getResource(o);
      if (resource != null) {
        result.add(resource);
        continue;
      }
      ResourceMapping mapping = Adapters.adapt(o, ResourceMapping.class);
      if (mapping == null)
        continue;
      try {
        for (ResourceTraversal t : mapping.getTraversals(ResourceMappingContext.LOCAL_CONTEXT, new NullProgressMonitor()))
          for (IResource r : t.getResources())
            if (r != null)
              result.add(r);
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return result.toArray(new IResource[0]);
  }

  /** {@code Utils.getResource}. VSCODE: the Requality nodes adapt to their file (IFile), not to IResource. */
  static IResource getResource(Object o) {
    IResource resource = Adapters.adapt(o, IResource.class);
    return resource != null ? resource : Adapters.adapt(o, IFile.class);
  }

  /** {@code TeamAction.getSelectedProjects}: the projects of the selected resources. */
  protected IProject[] getSelectedProjects() {
    List<IProject> projects = new ArrayList<>();
    for (IResource r : getSelectedResources())
      if (r.getType() == IResource.PROJECT)
        projects.add((IProject) r);
    return projects.toArray(new IProject[0]);
  }

  protected IStructuredSelection getSelection() {
    if (selection == null)
      selection = StructuredSelection.EMPTY;
    return selection;
  }

  /** {@code TeamAction.getShell}: the shell of the target part, else the active shell. */
  protected Shell getShell() {
    if (shell != null)
      return shell;
    if (targetPart != null && targetPart.getSite() != null && targetPart.getSite().getShell() != null)
      return targetPart.getSite().getShell();
    return Display.getDefault().getActiveShell();
  }

  /** {@code TeamAction.getTargetPart}: the part the action was started from. */
  protected IWorkbenchPart getTargetPart() {
    return targetPart;
  }

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    if (sel instanceof IStructuredSelection) {
      selection = (IStructuredSelection) sel;
      if (action != null)
        setActionEnablement(action);
    }
  }

  /** {@code TeamAction.setActionEnablement}: the enablement of the action from {@link #isEnabled()}. */
  protected void setActionEnablement(IAction action) {
    action.setEnabled(isEnabled());
  }

  /** {@code TeamAction.isEnabled}: whether the action applies to the selection. */
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void setActivePart(IAction action, IWorkbenchPart part) {
    if (part != null) {
      targetPart = part;
      shell = part.getSite() == null ? null : part.getSite().getShell();
    }
  }

  @Override
  public void run(IAction action) {
    try {
      execute(action);
    } catch (InvocationTargetException e) {
      handle(e);
    } catch (InterruptedException e) {
      // canceled by the user
    }
  }

  /** {@code TeamAction.execute}: the work of the action, run by {@link #run(IAction)}. */
  protected abstract void execute(IAction action) throws InvocationTargetException, InterruptedException;

  /** {@code TeamAction.handle}: shows the error with the title and the message. */
  protected void handle(Exception e, String title, String message) {
    Throwable t = e instanceof InvocationTargetException && ((InvocationTargetException) e).getTargetException() != null
        ? ((InvocationTargetException) e).getTargetException() : e;
    IStatus status = t instanceof CoreException && ((CoreException) t).getStatus() != null
        ? ((CoreException) t).getStatus() : new Status(IStatus.ERROR, "org.eclipse.team.ui", String.valueOf(t.getMessage()), t);
    ErrorDialog.openError(getShell(), title, message, status);
  }

  /** {@code TeamAction.handle}: shows the error with the title "Errors occurred". */
  protected void handle(Exception e) {
    handle(e, "Errors occurred", null);
  }
}
