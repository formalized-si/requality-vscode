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

package com.unitesk.requality.eclipse.wizards;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.eclipse.ui.perspective.ReqEditorPerspectiveFactory;

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
