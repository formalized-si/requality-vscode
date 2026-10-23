package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.resources.ICommand;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;
import com.unitesk.requality.vscode.ui.workbench.actions.BuildAction;
import com.unitesk.requality.vscode.ui.workbench.actions.CloseResourceAction;
import com.unitesk.requality.vscode.ui.workbench.actions.CloseUnrelatedProjectsAction;
import com.unitesk.requality.vscode.ui.workbench.actions.OpenResourceAction;
import com.unitesk.requality.vscode.ui.workbench.actions.RefreshAction;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.ResourceMgmtActionProvider}: Build Project (only when
 * the workspace does not build automatically), Refresh, Open Project, Close Project and Close Unrelated Projects in
 * group.build, and their global handlers.
 */
public class ResourceMgmtActionProvider extends CommonActionProvider {
  private BuildAction buildAction;
  private OpenResourceAction openProjectAction;
  private CloseResourceAction closeProjectAction;
  private CloseUnrelatedProjectsAction closeUnrelatedProjectsAction;
  private RefreshAction refreshAction;

  @Override
  public void init(ICommonActionExtensionSite aSite) {
    super.init(aSite);
    Supplier<Shell> shell = () -> aSite.getViewSite() == null ? null : aSite.getViewSite().getShell();
    buildAction = new BuildAction(shell, com.unitesk.requality.vscode.ui.resources.IncrementalProjectBuilder.INCREMENTAL_BUILD);
    openProjectAction = new OpenResourceAction(shell);
    closeProjectAction = new CloseResourceAction(shell);
    closeUnrelatedProjectsAction = new CloseUnrelatedProjectsAction(shell);
    refreshAction = new RefreshAction(shell);
  }

  private IStructuredSelection selection() {
    ISelection s = getContext() == null ? null : getContext().getSelection();
    return s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY;
  }

  /** The project has a builder in its build specification. */
  static boolean hasBuilder(IProject project) {
    try {
      ICommand[] commands = project.getDescription().getBuildSpec();
      return commands.length > 0;
    } catch (CoreException e) {
      return false;
    }
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    IStructuredSelection selection = selection();
    boolean isProjectSelection = true;
    boolean hasOpenProjects = false;
    boolean hasClosedProjects = false;
    boolean hasBuilder = true; // false if any project is closed or does not have a builder
    for (Object next : selection.toList()) {
      IProject project = null;
      if (next instanceof IProject)
        project = (IProject) next;
      else if (next instanceof IAdaptable)
        project = ((IAdaptable) next).getAdapter(IProject.class);
      if (project == null) {
        isProjectSelection = false;
        continue;
      }
      if (project.isOpen()) {
        hasOpenProjects = true;
        if (hasBuilder && !hasBuilder(project))
          hasBuilder = false;
      } else {
        hasClosedProjects = true;
        hasBuilder = false;
      }
    }
    if (!selection.isEmpty() && isProjectSelection && !ResourcesPlugin.getWorkspace().isAutoBuilding() && hasBuilder) {
      // allow manual incremental build only if auto build is off
      buildAction.selectionChanged(selection);
      menu.appendToGroup(ICommonMenuConstants.GROUP_BUILD, buildAction);
    }
    // the refresh item if any project is open, or for files and folders
    if (hasOpenProjects || !isProjectSelection && !selection.isEmpty()) {
      refreshAction.selectionChanged(selection);
      menu.appendToGroup(ICommonMenuConstants.GROUP_BUILD, refreshAction);
    }
    if (isProjectSelection) {
      if (hasClosedProjects) {
        openProjectAction.selectionChanged(selection);
        menu.appendToGroup(ICommonMenuConstants.GROUP_BUILD, openProjectAction);
      }
      if (hasOpenProjects) {
        closeProjectAction.selectionChanged(selection);
        menu.appendToGroup(ICommonMenuConstants.GROUP_BUILD, closeProjectAction);
        closeUnrelatedProjectsAction.selectionChanged(selection);
        menu.appendToGroup(ICommonMenuConstants.GROUP_BUILD, closeUnrelatedProjectsAction);
      }
    }
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    updateActionBars();
    actionBars.setGlobalActionHandler(ActionFactory.REFRESH.getId(), refreshAction);
    actionBars.setGlobalActionHandler(ActionFactory.BUILD_PROJECT.getId(), buildAction);
    actionBars.setGlobalActionHandler(ActionFactory.OPEN_PROJECT.getId(), openProjectAction);
    actionBars.setGlobalActionHandler(ActionFactory.CLOSE_PROJECT.getId(), closeProjectAction);
    actionBars.setGlobalActionHandler(ActionFactory.CLOSE_UNRELATED_PROJECTS.getId(), closeUnrelatedProjectsAction);
  }

  @Override
  public void updateActionBars() {
    if (getContext() == null)
      return; // the actions keep the selection they were last given
    IStructuredSelection selection = selection();
    refreshAction.selectionChanged(selection);
    buildAction.selectionChanged(selection);
    openProjectAction.selectionChanged(selection);
    closeProjectAction.selectionChanged(selection);
    closeUnrelatedProjectsAction.selectionChanged(selection);
  }
}
