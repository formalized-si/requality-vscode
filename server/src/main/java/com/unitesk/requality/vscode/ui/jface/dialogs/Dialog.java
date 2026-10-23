package com.unitesk.requality.vscode.ui.jface.dialogs;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.window.IShellProvider;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.dialogs.Dialog}: dialog area above a right-aligned button bar. */
public abstract class Dialog extends Window {
  public static final String DLG_IMG_MESSAGE_INFO = "dialog_message_info_image";
  public static final String DLG_IMG_MESSAGE_WARNING = "dialog_message_warning_image";
  public static final String DLG_IMG_MESSAGE_ERROR = "dialog_messasge_error_image";
  public static final String ELLIPSIS = "...";

  private final Map<Integer, Button> buttons = new HashMap<>();
  protected Control dialogArea;
  public Control buttonBar;

  protected Dialog(Shell parentShell) {
    super(parentShell);
    setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
  }

  protected Dialog(IShellProvider provider) {
    super(provider);
    setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.verticalSpacing = 0;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    dialogArea = createDialogArea(composite);
    buttonBar = createButtonBar(composite);
    return composite;
  }

  protected Control createDialogArea(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginHeight = 10;
    layout.marginWidth = 10;
    layout.verticalSpacing = 4;
    layout.horizontalSpacing = 4;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    return composite;
  }

  protected Control createButtonBar(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 0;
    layout.makeColumnsEqualWidth = true;
    layout.marginWidth = 10;
    layout.marginHeight = 8;
    layout.horizontalSpacing = 6;
    composite.setLayout(layout);
    GridData data = new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.VERTICAL_ALIGN_CENTER);
    composite.setLayoutData(data);
    composite.setCssClass("dialog-buttons");
    createButtonsForButtonBar(composite);
    return composite;
  }

  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    ((GridLayout) parent.getLayout()).numColumns++;
    parent.setLayout(parent.getLayout());
    Button button = new Button(parent, SWT.PUSH);
    button.setText(label);
    button.setData(Integer.valueOf(id));
    button.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        buttonPressed(((Integer) event.widget.getData()).intValue());
      }
    });
    if (defaultButton) {
      Shell shell = parent.getShell();
      if (shell != null)
        shell.setDefaultButton(button);
      button.setCssClass("default");
    }
    buttons.put(Integer.valueOf(id), button);
    button.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    return button;
  }

  protected Button getButton(int id) {
    return buttons.get(Integer.valueOf(id));
  }

  protected Control getButtonBar() {
    return buttonBar;
  }

  protected Control getDialogArea() {
    return dialogArea;
  }

  protected void buttonPressed(int buttonId) {
    if (IDialogConstants.OK_ID == buttonId)
      okPressed();
    else if (IDialogConstants.CANCEL_ID == buttonId)
      cancelPressed();
  }

  protected void okPressed() {
    setReturnCode(OK);
    close();
  }

  protected void cancelPressed() {
    setReturnCode(CANCEL);
    close();
  }

  protected boolean isResizable() {
    return false;
  }

  protected void applyDialogFont(Control control) {
  }

  public static void applyDialogFont(Control control, boolean b) {
  }

  protected int convertHorizontalDLUsToPixels(int dlus) {
    return (dlus * 6 + 2) / 4;
  }

  protected int convertVerticalDLUsToPixels(int dlus) {
    return (dlus * 16 + 4) / 8;
  }

  protected int convertWidthInCharsToPixels(int chars) {
    return chars * 7;
  }

  protected int convertHeightInCharsToPixels(int chars) {
    return chars * 18;
  }

  protected void setButtonLayoutData(Button button) {
    button.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
  }

  protected Object getDialogBoundsSettings() {
    return null;
  }

  @Override
  public boolean close() {
    buttons.clear();
    return super.close();
  }
}
