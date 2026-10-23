package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.compare.internal.CompareDialog}: a modal dialog that shows a compare editor input, with the OK
 * (Commit, Select) and Cancel buttons of the input and a status line.
 */
public class CompareDialog extends Dialog implements IPropertyChangeListener {
  private final CompareEditorInput fCompareEditorInput;
  private Button fCommitButton;
  private Label statusLabel;

  public CompareDialog(Shell shell, CompareEditorInput input) {
    super(shell);
    fCompareEditorInput = input;
  }

  @Override
  public boolean close() {
    if (super.close()) {
      fCompareEditorInput.removePropertyChangeListener(this);
      return true;
    }
    return false;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    fCommitButton = createButton(parent, IDialogConstants.OK_ID, fCompareEditorInput.getOKButtonLabel(), true);
    fCommitButton.setEnabled(isOKEnabled());
    if (isCancelable())
      createButton(parent, IDialogConstants.CANCEL_ID, fCompareEditorInput.getCancelButtonLabel(), false);
  }

  private boolean isCancelable() {
    return isInputEditable() || fCompareEditorInput.isEditionSelectionDialog();
  }

  protected boolean isInputEditable() {
    return fCompareEditorInput.getCompareConfiguration().isLeftEditable()
        || fCompareEditorInput.getCompareConfiguration().isRightEditable();
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (CompareEditorInput.DIRTY_STATE.equals(event.getProperty())
        || CompareEditorInput.PROP_SELECTED_EDITION.equals(event.getProperty())) {
      if (fCommitButton != null && !fCommitButton.isDisposed())
        fCommitButton.setEnabled(isOKEnabled());
    } else if (CompareEditorInput.PROP_TITLE.equals(event.getProperty())) {
      if (getShell() != null)
        getShell().setText(fCompareEditorInput.getTitle());
    }
  }

  private boolean isOKEnabled() {
    if (isInputEditable())
      return fCompareEditorInput.isDirty();
    if (fCompareEditorInput.isEditionSelectionDialog())
      return fCompareEditorInput.getSelectedEdition() != null;
    return true;
  }

  @Override
  protected Control createDialogArea(Composite parent2) {
    Composite parent = (Composite) super.createDialogArea(parent2);
    Control c = fCompareEditorInput.createContents(parent);
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    statusLabel = new Label(parent, SWT.NONE);
    statusLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Shell shell = c.getShell();
    shell.setText(fCompareEditorInput.getTitle());
    return parent;
  }

  /** The status line of the dialog. */
  void setStatusMessage(String message) {
    if (statusLabel != null && !statusLabel.isDisposed())
      statusLabel.setText(message == null ? "" : message);
  }

  @Override
  public int open() {
    fCompareEditorInput.addPropertyChangeListener(this);
    fCompareEditorInput.setContainer(this);
    return super.open();
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == OK) {
      if (!fCompareEditorInput.okPressed())
        return;
    } else if (buttonId == CANCEL) {
      fCompareEditorInput.cancelPressed();
    }
    super.buttonPressed(buttonId);
  }

  @Override
  protected boolean isResizable() {
    return true;
  }
}
