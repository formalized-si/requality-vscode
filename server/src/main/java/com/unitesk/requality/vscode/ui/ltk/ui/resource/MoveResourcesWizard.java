package com.unitesk.requality.vscode.ui.ltk.ui.resource;

import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.ltk.ProcessorBasedRefactoring;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.ltk.resource.MoveResourcesProcessor;
import com.unitesk.requality.vscode.ui.ltk.ui.RefactoringWizard;
import com.unitesk.requality.vscode.ui.ltk.ui.UserInputWizardPage;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.ltk.ui.refactoring.resource.MoveResourcesWizard}: "Move Resources" with the tree of the destination
 * containers ("Choose destination for '...':").
 */
public class MoveResourcesWizard extends RefactoringWizard {

  /** {@code MoveResourcesWizard(IResource[])}. */
  public MoveResourcesWizard(IResource[] resources) {
    super(new ProcessorBasedRefactoring(new MoveResourcesProcessor(resources)), DIALOG_BASED_USER_INTERFACE);
    setDefaultPageTitle("Move Resources");
    setWindowTitle("Move Resources");
  }

  @Override
  protected void addUserInputPages() {
    MoveResourcesProcessor processor = (MoveResourcesProcessor) ((ProcessorBasedRefactoring) getRefactoring())
        .getProcessor();
    addPage(new MoveResourcesRefactoringConfigurationPage(processor));
  }

  /** {@code MoveResourcesWizard.MoveResourcesRefactoringConfigurationPage}: the destination tree. */
  private static class MoveResourcesRefactoringConfigurationPage extends UserInputWizardPage {
    private final MoveResourcesProcessor processor;
    private TreeViewer destinationField;

    MoveResourcesRefactoringConfigurationPage(MoveResourcesProcessor processor) {
      super("MoveResourcesRefactoringConfigurationPage");
      this.processor = processor;
    }

    @Override
    public void createControl(Composite parent) {
      Composite composite = new Composite(parent, SWT.NONE);
      composite.setLayout(new GridLayout(1, false));
      composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      IResource[] resourcesToMove = processor.getResourcesToMove();
      Label label = new Label(composite, SWT.NONE);
      label.setText(resourcesToMove.length == 1
          ? "Choose destination for '" + resourcesToMove[0].getName() + "':"
          : "Choose destination for " + resourcesToMove.length + " selected resources:");
      label.setLayoutData(new GridData());
      destinationField = new TreeViewer(composite, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
      destinationField.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      destinationField.setLabelProvider(WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider());
      destinationField.setContentProvider(new ContainerContentProvider());
      destinationField.setInput(ResourcesPlugin.getWorkspace());
      destinationField.addFilter(new ViewerFilter() {
        @Override
        public boolean select(com.unitesk.requality.vscode.ui.jface.viewers.Viewer viewer, Object parentElement,
            Object element) {
          if (element instanceof IProject)
            return ((IProject) element).isAccessible();
          return element instanceof IContainer;
        }
      });
      destinationField.addSelectionChangedListener(e -> validatePage());
      if (resourcesToMove.length > 0 && resourcesToMove[0].getParent() != null)
        destinationField.setSelection(new StructuredSelection(resourcesToMove[0].getParent()));
      setPageComplete(false);
      setControl(composite);
      validatePage();
    }

    @Override
    public void setVisible(boolean visible) {
      if (visible && destinationField != null)
        destinationField.getTree().setFocus();
      super.setVisible(visible);
    }

    private void validatePage() {
      RefactoringStatus status;
      IStructuredSelection selection = (IStructuredSelection) destinationField.getSelection();
      Object first = selection.getFirstElement();
      if (first instanceof IContainer)
        status = processor.validateDestination((IContainer) first);
      else {
        status = new RefactoringStatus();
        status.addFatalError("Select an resource.");
      }
      setPageComplete(status);
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
      Object first = ((IStructuredSelection) destinationField.getSelection()).getFirstElement();
      processor.setDestination(first instanceof IContainer ? (IContainer) first : null);
    }
  }

  /** The projects and folders of the workspace, as the tree of the destination. */
  private static final class ContainerContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return getChildren(inputElement);
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      try {
        if (parentElement instanceof IWorkspace)
          return ((IWorkspace) parentElement).getRoot().getProjects();
        if (parentElement instanceof IWorkspaceRoot)
          return ((IWorkspaceRoot) parentElement).getProjects();
        if (parentElement instanceof IContainer) {
          java.util.List<IResource> r = new java.util.ArrayList<>();
          for (IResource m : ((IContainer) parentElement).members())
            if (m instanceof IContainer)
              r.add(m);
          return r.toArray();
        }
      } catch (CoreException e) {
        return new Object[0];
      }
      return new Object[0];
    }

    @Override
    public Object getParent(Object element) {
      return element instanceof IResource ? ((IResource) element).getParent() : null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return getChildren(element).length > 0;
    }
  }
}
