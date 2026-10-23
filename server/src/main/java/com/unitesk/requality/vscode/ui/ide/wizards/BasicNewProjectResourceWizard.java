package com.unitesk.requality.vscode.ui.ide.wizards;

import java.net.URI;

import com.unitesk.requality.vscode.ui.ide.undo.CreateProjectOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WizardNewProjectCreationPage;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WizardNewProjectReferencePage;

/**
 * {@code org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard}: the new project wizard (General > Project).
 * The first page asks for the name and the location of the project, the second one (when the workspace has projects)
 * for the referenced projects. Finish creates the project with the undoable {@link CreateProjectOperation}.
 */
public class BasicNewProjectResourceWizard extends BasicNewResourceWizard {
  /** The wizard id of the new project wizard. */
  public static final String WIZARD_ID = "org.eclipse.ui.wizards.new.project";

  private WizardNewProjectCreationPage mainPage;
  private WizardNewProjectReferencePage referencePage;
  private IProject newProject;

  /** Creates a wizard for creating a new project resource in the workspace. */
  public BasicNewProjectResourceWizard() {
    super();
  }

  @Override
  public void addPages() {
    super.addPages();
    mainPage = new WizardNewProjectCreationPage("basicNewProjectPage") {
      @Override
      public void createControl(Composite parent) {
        super.createControl(parent);
        createWorkingSetGroup((Composite) getControl(), BasicNewProjectResourceWizard.this.getSelection(),
            new String[] { "org.eclipse.ui.resourceWorkingSetPage" });
      }
    };
    mainPage.setTitle("Project");
    mainPage.setDescription("Create a new project resource.");
    this.addPage(mainPage);
    if (ResourcesPlugin.getWorkspace().getRoot().getProjects().length > 0) {
      referencePage = new WizardNewProjectReferencePage("basicReferenceProjectPage");
      referencePage.setTitle("Project References");
      referencePage.setDescription("Select referenced projects.");
      this.addPage(referencePage);
    }
  }

  /** Creates the project named by the first page, once; null when the creation failed. */
  private IProject createNewProject() {
    if (newProject != null)
      return newProject;
    final IProject newProjectHandle = mainPage.getProjectHandle();
    URI location = null;
    if (!mainPage.useDefaults())
      location = mainPage.getLocationURI();
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    final IProjectDescription description = workspace.newProjectDescription(newProjectHandle.getName());
    description.setLocationURI(location);
    if (referencePage != null) {
      IProject[] refProjects = referencePage.getReferencedProjects();
      if (refProjects.length > 0)
        description.setReferencedProjects(refProjects);
    }
    CreateProjectOperation op = new CreateProjectOperation(description, "New Project");
    try {
      WorkspaceUndoUtil.execute(op, new NullProgressMonitor(), getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), "Creation Problems", null, e.getStatus());
      return null;
    }
    newProject = newProjectHandle;
    return newProject;
  }

  /** The project created by the wizard, or null. */
  public IProject getNewProject() {
    return newProject;
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
    super.init(workbench, currentSelection);
    setNeedsProgressMonitor(true);
    setWindowTitle("New Project");
  }

  @Override
  public boolean performFinish() {
    createNewProject();
    if (newProject == null)
      return false;
    IWorkingSet[] workingSets = mainPage.getSelectedWorkingSets();
    getWorkbench().getWorkingSetManager().addToWorkingSets(newProject, workingSets);
    selectAndReveal(newProject);
    return true;
  }
}
