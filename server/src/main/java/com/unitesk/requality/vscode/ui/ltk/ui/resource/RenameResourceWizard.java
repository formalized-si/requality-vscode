package com.unitesk.requality.vscode.ui.ltk.ui.resource;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.ltk.ProcessorBasedRefactoring;
import com.unitesk.requality.vscode.ui.ltk.ui.RefactoringWizard;
import com.unitesk.requality.vscode.ui.ltk.ui.UserInputWizardPage;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.ltk.resource.RenameResourceProcessor;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ltk.ui.refactoring.resource.RenameResourceWizard}: "Rename Resource" with the "New name:" field. The
 * name of a file is selected without its extension.
 */
public class RenameResourceWizard extends RefactoringWizard {

  /** {@code RenameResourceWizard(IResource)}. */
  public RenameResourceWizard(IResource resource) {
    super(new ProcessorBasedRefactoring(new RenameResourceProcessor(resource)), DIALOG_BASED_USER_INTERFACE);
    setDefaultPageTitle("Rename Resource");
    setWindowTitle("Rename Resource");
  }

  /** {@code RenameResourceWizard(IResource, String)}: the new name is set to newName. */
  public RenameResourceWizard(IResource resource, String newName) {
    this(resource);
    getProcessor().setNewResourceName(newName);
  }

  private RenameResourceProcessor getProcessor() {
    return (RenameResourceProcessor) ((ProcessorBasedRefactoring) getRefactoring()).getProcessor();
  }

  @Override
  protected void addUserInputPages() {
    addPage(new RenameResourceRefactoringConfigurationPage(getProcessor()));
  }

  /** {@code RenameResourceWizard.RenameResourceRefactoringConfigurationPage}: the "New name:" field. */
  public static class RenameResourceRefactoringConfigurationPage extends UserInputWizardPage {
    private final RenameResourceProcessor processor;
    private Text nameField;

    public RenameResourceRefactoringConfigurationPage(RenameResourceProcessor processor) {
      super("RenameResourceRefactoringInputPage");
      this.processor = processor;
    }

    @Override
    public void createControl(Composite parent) {
      Composite composite = new Composite(parent, SWT.NONE);
      composite.setLayout(new GridLayout(2, false));
      composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      Label label = new Label(composite, SWT.NONE);
      label.setText("New name:");
      label.setLayoutData(new GridData());
      nameField = new Text(composite, SWT.BORDER);
      String resourceName = processor.getNewResourceName();
      nameField.setText(resourceName);
      nameField.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
      nameField.addModifyListener(e -> validatePage());
      int lastIndexOfDot = resourceName.lastIndexOf('.');
      if (processor.getResource().getType() == IResource.FILE && lastIndexOfDot > 0)
        nameField.setSelection(0, lastIndexOfDot);
      else
        nameField.setSelection(0, resourceName.length());
      setPageComplete(false);
      setControl(composite);
      validatePage();
    }

    @Override
    public void setVisible(boolean visible) {
      if (visible && nameField != null)
        nameField.setFocus();
      super.setVisible(visible);
    }

    private void validatePage() {
      RefactoringStatus status = processor.validateNewElementName(nameField.getText());
      setPageComplete(status);
    }

    @Override
    protected boolean performFinish() {
      initializeRefactoring();
      return super.performFinish();
    }

    @Override
    public IWizardPage getNextPage() {
      initializeRefactoring();
      return super.getNextPage();
    }

    private void initializeRefactoring() {
      processor.setNewResourceName(nameField.getText());
    }
  }
}
