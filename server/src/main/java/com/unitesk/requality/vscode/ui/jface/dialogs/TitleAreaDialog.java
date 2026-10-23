package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.dialogs.TitleAreaDialog}: a title and a message (or error) above the dialog area. */
public class TitleAreaDialog extends Dialog {
  private Label titleLabel;
  private Label messageLabel;
  private String message = "";
  private int messageType = IMessageProvider.NONE;
  private String errorMessage;
  private Composite workArea;

  public TitleAreaDialog(Shell parentShell) {
    super(parentShell);
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite contents = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.verticalSpacing = 0;
    contents.setLayout(layout);
    contents.setLayoutData(new GridData(GridData.FILL_BOTH));
    Composite title = new Composite(contents, SWT.NONE);
    GridLayout tl = new GridLayout();
    tl.marginWidth = 10;
    tl.marginHeight = 8;
    title.setLayout(tl);
    title.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    title.setCssClass("dialog-title-area");
    titleLabel = new Label(title, SWT.NONE);
    titleLabel.setCssClass("dialog-title");
    titleLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    messageLabel = new Label(title, SWT.WRAP);
    messageLabel.setCssClass("dialog-message");
    messageLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    workArea = new Composite(contents, SWT.NONE);
    GridLayout wl = new GridLayout();
    wl.marginHeight = 0;
    wl.marginWidth = 0;
    wl.verticalSpacing = 0;
    workArea.setLayout(wl);
    workArea.setLayoutData(new GridData(GridData.FILL_BOTH));
    dialogArea = createDialogArea(workArea);
    buttonBar = createButtonBar(workArea);
    return contents;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.verticalSpacing = 0;
    layout.horizontalSpacing = 0;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    return composite;
  }

  public void setTitle(String newTitle) {
    if (titleLabel != null)
      titleLabel.setText(newTitle == null ? "" : newTitle);
  }

  public void setTitleImage(Image image) {
  }

  public void setMessage(String newMessage) {
    setMessage(newMessage, IMessageProvider.NONE);
  }

  public void setMessage(String newMessage, int type) {
    message = newMessage == null ? "" : newMessage;
    messageType = type;
    showMessage();
  }

  public String getMessage() {
    return message;
  }

  public void setErrorMessage(String newErrorMessage) {
    errorMessage = newErrorMessage;
    showMessage();
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  private void showMessage() {
    if (messageLabel == null || messageLabel.isDisposed())
      return;
    if (errorMessage != null) {
      messageLabel.setText(errorMessage);
      messageLabel.setCssClass("dialog-message error");
    } else {
      messageLabel.setText(message);
      messageLabel.setCssClass(messageType == IMessageProvider.ERROR ? "dialog-message error"
          : messageType == IMessageProvider.WARNING ? "dialog-message warning" : "dialog-message");
    }
  }

  protected Label getTitleImageLabel() {
    return titleLabel;
  }

  public void setTitleAreaColor(Object rgb) {
  }
}
