package com.unitesk.requality.vscode.ui.ide.actions;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.ide.dialogs.CleanDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IncrementalProjectBuilder;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.actions.BuildAction;

/**
 * The workbench window actions of the IDE Project menu that are not retargeted to a view: Build All
 * ({@code GlobalBuildAction}), Clean... ({@code BuildCleanAction}) and Build Automatically
 * ({@code ToggleAutoBuildAction}).
 */
public final class ProjectHandlers {
  private ProjectHandlers() {
  }

  /** The projects in the current selection. */
  static IProject[] selectedProjects(ExecutionEvent event) {
    ISelection s = HandlerUtil.getCurrentSelection(event);
    List<IProject> r = new ArrayList<>();
    if (s instanceof IStructuredSelection)
      for (Object o : ((IStructuredSelection) s).toList())
        if (o instanceof IProject && ((IProject) o).isOpen())
          r.add((IProject) o);
    return r.toArray(new IProject[0]);
  }

  /** Project > Build All: an incremental build of the workspace; enabled when it does not build automatically. */
  public static final class BuildAll extends AbstractHandler {
    @Override
    public boolean isEnabled() {
      return !ResourcesPlugin.getWorkspace().isAutoBuilding();
    }

    @Override
    public Object execute(ExecutionEvent event) {
      BuildAction.schedule(null, IncrementalProjectBuilder.INCREMENTAL_BUILD);
      return null;
    }
  }

  /** Project > Clean...: the Clean dialog with the selected projects checked. */
  public static final class Clean extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
      new CleanDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), selectedProjects(event)).open();
      return null;
    }
  }

  /** Project > Build Automatically: toggles the workspace auto-build (a build follows when it is switched on). */
  public static final class ToggleAutoBuild extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
      boolean on = !ResourcesPlugin.getWorkspace().isAutoBuilding();
      ServerContext.server().workspace().setAutoBuilding(on);
      ServerContext.client().setContext("requality.autoBuilding", on);
      if (on)
        BuildAction.schedule(null, IncrementalProjectBuilder.AUTO_BUILD);
      return null;
    }
  }
}
