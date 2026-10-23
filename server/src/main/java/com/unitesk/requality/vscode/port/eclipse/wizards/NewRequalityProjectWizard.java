/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.vscode.port.eclipse.wizards;

import com.unitesk.requality.vscode.port.eclipse.ui.perspective.ReqEditorPerspectiveFactory;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.INewWizard;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWizard;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.WorkbenchException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.vscode.port.eclipse.natures.ReqCatalogue;

public class NewRequalityProjectWizard extends Wizard implements INewWizard {
  private static final String REQUALITY_PERSPECTIVE_PREFIX = "com.unitesk.requality.";
  private NewRequalityProjectPage page;
  private ISelection selection;
  /** Constructor for ReqWizard. */
  public NewRequalityProjectWizard() {
    super();
    setNeedsProgressMonitor(true);
  }

  /** Adding the page to the wizard. */
  public void addPages() {
    page = new NewRequalityProjectPage(selection);
    addPage(page);
  }

  public static TreeDB createProject(String lname, String pname) {
    return ResManager.getManager().getTracker().createProject(lname, pname);
  }

  @Override
  public boolean performFinish() {
    final String projectName = page.getContainerName();
    WorkspaceJob wj = new WorkspaceJob("Create project "+projectName){
    
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        createProject(projectName, projectName);
        return Status.OK_STATUS;
      }
    };
    wj.setPriority(wj.SHORT);
    wj.schedule();
    return true;
  }

  public static IProject performFinishNewElement(String projectName) throws CoreException {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IWorkspaceRoot root = workspace.getRoot();
    IProject project = root.getProject(projectName);
    IProjectDescription desc = workspace.newProjectDescription(projectName);
    desc.setNatureIds(new String[] {ReqCatalogue.ID});
    project.create(desc, null);
    project.open(null);
    project.setDefaultCharset("UTF-8", null);
    return project;
  }

  private static void updatePerspective() throws WorkbenchException {
    IWorkbench a = PlatformUI.getWorkbench();
    if (a == null) RLogger.logInfo("Workbench is null!");
    else {
      IWorkbenchWindow b = a.getActiveWorkbenchWindow();
      if (b == null) {
        RLogger.logInfo("Workbench window is null!");
      } else {
        IWorkbenchPage ap = b.getActivePage();
        if (ap == null) RLogger.logInfo("Workbench page is null!");
        else if (ap.getPerspective() == null) RLogger.logInfo("Perspective is null!");
        else if (!ap.getPerspective().getId().startsWith(REQUALITY_PERSPECTIVE_PREFIX)) {
          IPerspectiveRegistry reg = PlatformUI.getWorkbench().getPerspectiveRegistry();
          IPerspectiveDescriptor perspective =
              reg.findPerspectiveWithId(ReqEditorPerspectiveFactory.ID);
          ap.setPerspective(perspective);
        }
      }
    }
  }

  /**
   * We will accept the selection in the workbench to see if we can initialize from it.
   *
   * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
   */
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    this.selection = selection;
  }
}
