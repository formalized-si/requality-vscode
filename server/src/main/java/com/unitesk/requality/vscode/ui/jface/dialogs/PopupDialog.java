package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.dialogs.PopupDialog}: title, dialog area and info line, closed with Escape. */
public class PopupDialog extends Window {
  public static final int INFOPOPUP_SHELLSTYLE = SWT.NO_TRIM;
  public static final int INFOPOPUPRESIZE_SHELLSTYLE = SWT.RESIZE;
  public static final int HOVER_SHELLSTYLE = SWT.NO_FOCUS | SWT.ON_TOP | SWT.TOOL;
  private String titleText;
  private String infoText;
  private Label titleLabel;
  private Label infoLabel;

  public PopupDialog(Shell parent, int shellStyle, boolean takeFocusOnOpen, boolean persistSize,
      boolean persistLocation, boolean showDialogMenu, boolean showPersistActions, String titleText, String infoText) {
    super(parent);
    setShellStyle(shellStyle);
    this.titleText = titleText;
    this.infoText = infoText;
    setBlockOnOpen(false);
  }

  public PopupDialog(Shell parent, int shellStyle, boolean takeFocusOnOpen, boolean persistBounds,
      boolean showDialogMenu, boolean showPersistActions, String titleText, String infoText) {
    this(parent, shellStyle, takeFocusOnOpen, persistBounds, persistBounds, showDialogMenu, showPersistActions,
        titleText, infoText);
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    if (titleText != null)
      shell.setText(titleText);
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite c = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout();
    gl.marginWidth = 6;
    gl.marginHeight = 6;
    c.setLayout(gl);
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    if (hasTitleArea()) {
      titleLabel = new Label(c, SWT.NONE);
      titleLabel.setText(titleText == null ? "" : titleText);
      titleLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    }
    Control area = createDialogArea(c);
    if (area != null && area.getLayoutData() == null)
      area.setLayoutData(new GridData(GridData.FILL_BOTH));
    if (hasInfoArea()) {
      infoLabel = new Label(c, SWT.NONE);
      infoLabel.setText(infoText);
      infoLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    }
    return c;
  }

  protected Control createDialogArea(Composite parent) {
    Composite c = new Composite(parent, SWT.NONE);
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    return c;
  }

  protected boolean hasTitleArea() {
    return titleText != null;
  }

  protected boolean hasInfoArea() {
    return infoText != null;
  }

  public void setTitleText(String text) {
    titleText = text;
    if (titleLabel != null && !titleLabel.isDisposed())
      titleLabel.setText(text == null ? "" : text);
    if (getShell() != null && !getShell().isDisposed() && text != null)
      getShell().setText(text);
  }

  public void setInfoText(String text) {
    infoText = text;
    if (infoLabel != null && !infoLabel.isDisposed())
      infoLabel.setText(text == null ? "" : text);
  }

  protected Control getFocusControl() {
    return null;
  }

  protected void setTabOrder(Composite composite) {
  }

  protected void fillDialogMenu(com.unitesk.requality.vscode.ui.jface.action.IMenuManager dialogMenu) {
  }

  protected com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings getDialogSettings() {
    return null;
  }
}
