package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.jface.dialogs.InputDialog}: a dialog with a message, a text field, an error line validated on every
 * change (the OK button is disabled while the validator reports an error) and OK/Cancel buttons. Shown as a VS Code
 * panel like the other dialogs.
 */
public class InputDialog extends Dialog {
  private final String title;
  private final String message;
  private String value;
  private final IInputValidator validator;
  private Text text;
  private Label errorMessageText;
  private String errorMessage;

  public InputDialog(Shell parentShell, String dialogTitle, String dialogMessage, String initialValue,
      IInputValidator validator) {
    super(parentShell);
    this.title = dialogTitle;
    this.message = dialogMessage;
    if (initialValue == null)
      this.value = "";
    else
      this.value = initialValue;
    this.validator = validator;
  }

  @Override
  public void create() {
    super.create();
    validateInput();
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    if (title != null)
      newShell.setText(title);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    Label label = new Label(composite, SWT.WRAP);
    if (message != null)
      label.setText(message);
    label.setLayoutData(new GridData(GridData.FILL_BOTH));
    text = new Text(composite, getInputTextStyle());
    text.setText(value);
    text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    text.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        validateInput();
      }
    });
    errorMessageText = new Label(composite, SWT.WRAP);
    errorMessageText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    errorMessageText.setVisible(false);
    return composite;
  }

  /** The style of the text field. */
  protected int getInputTextStyle() {
    return SWT.SINGLE | SWT.BORDER;
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (IDialogConstants.OK_ID == buttonId)
      value = text.getText();
    else
      value = null;
    super.buttonPressed(buttonId);
  }

  /** Validates the text and shows the validator's message; the OK button is enabled only without a message. */
  protected void validateInput() {
    String error = null;
    if (validator != null)
      error = validator.isValid(text.getText());
    setErrorMessage(error);
  }

  /** Shows the error (or information) message under the text field; {@code null} clears it. */
  protected void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    if (errorMessageText != null && !errorMessageText.isDisposed()) {
      errorMessageText.setText(errorMessage == null ? "" : errorMessage);
      boolean hasError = errorMessage != null && !errorMessage.replaceAll("\\s+", "").isEmpty();
      errorMessageText.setEnabled(hasError);
      errorMessageText.setVisible(hasError);
      errorMessageText.getParent().update();
      Control button = getButton(IDialogConstants.OK_ID);
      if (button != null)
        button.setEnabled(errorMessage == null);
    }
  }

  protected String getErrorMessage() {
    return errorMessage;
  }

  /** The text field, or null before the dialog area is created. */
  protected Text getText() {
    return text;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String newValue) {
    this.value = newValue;
  }
}
