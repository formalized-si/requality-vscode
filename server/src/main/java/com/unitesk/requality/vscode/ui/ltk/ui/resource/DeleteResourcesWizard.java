package com.unitesk.requality.vscode.ui.ltk.ui.resource;

import java.util.Arrays;

import com.unitesk.requality.vscode.ui.ltk.ProcessorBasedRefactoring;
import com.unitesk.requality.vscode.ui.ltk.ui.RefactoringWizard;
import com.unitesk.requality.vscode.ui.ltk.ui.UserInputWizardPage;
import com.unitesk.requality.vscode.ui.ltk.resource.DeleteResourcesProcessor;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ltk.ui.refactoring.resource.DeleteResourcesWizard}: "Delete Resources". The question is "Delete '...'
 * from the file system?" for files and folders; for projects it is "Remove project '...' from the workspace?" with the
 * option to delete the project contents on disk and the project locations.
 */
public class DeleteResourcesWizard extends RefactoringWizard {

  /** {@code DeleteResourcesWizard(IResource[])}. */
  public DeleteResourcesWizard(IResource[] resources) {
    super(new ProcessorBasedRefactoring(new DeleteResourcesProcessor(resources)), DIALOG_BASED_USER_INTERFACE);
    setDefaultPageTitle("Delete Resources");
    setWindowTitle("Delete Resources");
  }

  @Override
  protected void addUserInputPages() {
    DeleteResourcesProcessor processor = (DeleteResourcesProcessor) ((ProcessorBasedRefactoring) getRefactoring())
        .getProcessor();
    addPage(new DeleteResourcesRefactoringConfigurationPage(processor));
  }

  /** {@code DeleteResourcesWizard.DeleteResourcesRefactoringConfigurationPage}. */
  private static class DeleteResourcesRefactoringConfigurationPage extends UserInputWizardPage {
    private final DeleteResourcesProcessor processor;
    private Button deleteContentsButton;

    DeleteResourcesRefactoringConfigurationPage(DeleteResourcesProcessor processor) {
      super("DeleteResourcesRefactoringConfigurationPage");
      this.processor = processor;
    }

    @Override
    public void createControl(Composite parent) {
      Composite composite = new Composite(parent, SWT.NONE);
      composite.setLayout(new GridLayout(1, false));
      composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      IResource[] resources = processor.getResourcesToDelete();
      boolean onlyProjects = resources.length > 0 && Arrays.stream(resources)
          .allMatch(r -> r.getType() == IResource.PROJECT);
      Label label = new Label(composite, SWT.WRAP);
      label.setText(question(resources, onlyProjects));
      label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
      if (onlyProjects) {
        final IResource[] initialResources = resources;
        java.util.Set<IProject> nestedProjects = computeNestedProjects(initialResources);
        deleteContentsButton = new Button(composite, SWT.CHECK);
        deleteContentsButton.setText("&Delete project contents on disk (cannot be undone)");
        deleteContentsButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (!nestedProjects.isEmpty()) {
          java.util.Set<IResource> projectHierarchy = new java.util.LinkedHashSet<>(Arrays.asList(initialResources));
          projectHierarchy.addAll(nestedProjects);
          Button deleteNestedProjectsCheckbox = new Button(composite, SWT.CHECK);
          deleteNestedProjectsCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
          deleteNestedProjectsCheckbox.setText(nestedProjects.size() == 1 ? "Delete 1 &nested project"
              : "Delete " + nestedProjects.size() + " &nested projects");
          Runnable nestedChanged = () -> {
            processor.setResources(deleteNestedProjectsCheckbox.getSelection()
                ? projectHierarchy.toArray(new IResource[0]) : initialResources);
            updateListOfProjects();
          };
          deleteNestedProjectsCheckbox.addListener(SWT.Selection, e -> nestedChanged.run());
          // deleting the contents on disk deletes the nested projects too
          deleteContentsButton.addListener(SWT.Selection, new com.unitesk.requality.vscode.ui.swt.widgets.Listener() {
            private boolean previousNestedProjectSelection;

            @Override
            public void handleEvent(com.unitesk.requality.vscode.ui.swt.widgets.Event e) {
              if (deleteContentsButton.getSelection()) {
                previousNestedProjectSelection = deleteNestedProjectsCheckbox.getSelection();
                deleteNestedProjectsCheckbox.setSelection(true);
              } else {
                deleteNestedProjectsCheckbox.setSelection(previousNestedProjectSelection);
              }
              deleteNestedProjectsCheckbox.setEnabled(!deleteContentsButton.getSelection());
              nestedChanged.run();
            }
          });
        }
        locationLabel = new Label(composite, SWT.NONE);
        locationLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        locations = new Text(composite, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
        locations.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        updateListOfProjects();
      }
      setPageComplete(true);
      setControl(composite);
    }

    private Label locationLabel;
    private Text locations;

    /** The locations of the projects to delete ("Project location:" / "n project locations:"). */
    private void updateListOfProjects() {
      IResource[] resources = processor.getResourcesToDelete();
      StringBuilder sb = new StringBuilder();
      for (IResource r : resources) {
        if (sb.length() > 0)
          sb.append('\n');
        sb.append(r.getLocation() == null ? r.getName() : r.getLocation().toOSString());
      }
      locations.setText(sb.toString());
      locationLabel.setText(resources.length == 1 ? "&Project location:" : resources.length + " &project locations:");
    }

    /**
     * {@code DeleteResourcesWizard.computeNestedProjects}: the projects of the workspace located inside the projects to
     * delete.
     */
    private static java.util.Set<IProject> computeNestedProjects(IResource[] initialResources) {
      java.util.Set<IProject> result = new java.util.LinkedHashSet<>();
      if (initialResources == null || initialResources.length == 0)
        return result;
      java.util.Set<IResource> initial = new java.util.HashSet<>(Arrays.asList(initialResources));
      for (IProject project : com.unitesk.requality.vscode.ui.resources.ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
        if (initial.contains(project) || project.getLocation() == null)
          continue;
        for (IResource r : initialResources) {
          if (r.getLocation() != null && r.getLocation().isPrefixOf(project.getLocation())) {
            result.add(project);
            break;
          }
        }
      }
      return result;
    }

    private static String question(IResource[] resources, boolean onlyProjects) {
      if (onlyProjects) {
        if (resources.length == 1)
          return "Remove project '" + ProjectRef.displayName((IProject) resources[0]) + "' from the workspace?";
        return "Remove these " + resources.length + " projects from the workspace?";
      }
      boolean linked = Arrays.stream(resources).anyMatch(IResource::isLinked);
      if (linked) {
        // VSCODE: linked resources are not created by this port; the workspace links keep their targets
        if (resources.length == 1)
          return "Delete linked resource '" + resources[0].getName()
              + "'?\n\nOnly the workspace link will be deleted. Link target will remain unchanged.";
        return "Delete these " + resources.length
            + " resources?\n\nSelection contains linked resources.\nOnly the workspace links will be deleted. Link targets will remain unchanged.";
      }
      if (resources.length == 1)
        return "Delete '" + resources[0].getName() + "' from the file system?";
      return "Delete the " + resources.length + " selected resources from the file system?";
    }

    @Override
    protected boolean performFinish() {
      initializeRefactoring();
      return super.performFinish();
    }

    @Override
    public com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage getNextPage() {
      initializeRefactoring();
      return super.getNextPage();
    }

    private void initializeRefactoring() {
      processor.setDeleteContents(deleteContentsButton != null && deleteContentsButton.getSelection());
    }
  }
}
