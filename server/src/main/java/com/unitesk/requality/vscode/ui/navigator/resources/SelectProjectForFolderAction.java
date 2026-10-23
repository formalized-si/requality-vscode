package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.Collection;
import java.util.Collections;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.SelectProjectForFolderAction}: selects the projects of
 * the folders in the navigator ("Go to corresponding project").
 */
public class SelectProjectForFolderAction extends Action {
  private final Collection<IProject> project;
  private final CommonViewer viewer;

  public SelectProjectForFolderAction(Collection<IProject> project, CommonViewer viewer) {
    super("&Go to corresponding projects");
    this.project = project;
    this.viewer = viewer;
    setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_PROJECT));
  }

  public SelectProjectForFolderAction(IProject project, CommonViewer viewer) {
    super("&Go to project '" + project.getName() + "'");
    this.project = Collections.singleton(project);
    this.viewer = viewer;
    setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_PROJECT));
  }

  @Override
  public void run() {
    viewer.setSelection(new StructuredSelection(new java.util.ArrayList<>(project)));
  }
}
