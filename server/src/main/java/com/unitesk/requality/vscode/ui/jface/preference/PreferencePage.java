package com.unitesk.requality.vscode.ui.jface.preference;

import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.preference.PreferencePage}. */
public abstract class PreferencePage implements IPreferencePage, IMessageProvider {
  private String title = "";
  private String description;
  private Control control;
  private PreferenceDialog container;
  private boolean valid = true;
  private String message;
  private int messageType;
  private String errorMessage;
  private IPreferenceStore store;
  private boolean noDefaultAndApply;

  protected PreferencePage() {
  }

  protected PreferencePage(String title) {
    this.title = title;
  }

  protected PreferencePage(String title, Object image) {
    this.title = title;
  }

  protected abstract Control createContents(Composite parent);

  @Override
  public void createControl(Composite parent) {
    Composite content = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout();
    gl.marginWidth = 0;
    gl.marginHeight = 0;
    content.setLayout(gl);
    content.setLayoutData(new GridData(GridData.FILL_BOTH));
    Control c = createContents(content);
    if (c != null && c.getLayoutData() == null)
      c.setLayoutData(new GridData(GridData.FILL_BOTH));
    control = content;
  }

  @Override
  public Control getControl() {
    return control;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public void setDescription(String d) {
    description = d;
  }

  public String getDescription() {
    return description;
  }

  public void setImageDescriptor(Object desc) {
  }

  protected void noDefaultAndApplyButton() {
    noDefaultAndApply = true;
  }

  public boolean hasDefaultAndApply() {
    return !noDefaultAndApply;
  }

  @Override
  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean b) {
    valid = b;
    if (container != null)
      container.updateButtons();
  }

  @Override
  public boolean okToLeave() {
    return isValid();
  }

  @Override
  public boolean performOk() {
    return true;
  }

  @Override
  public boolean performCancel() {
    return true;
  }

  protected void performDefaults() {
  }

  protected void performApply() {
    performOk();
  }

  protected void performHelp() {
  }

  @Override
  public void setContainer(PreferenceDialog container) {
    this.container = container;
  }

  public PreferenceDialog getContainer() {
    return container;
  }

  /** {@code DialogPage.getShell()}: the shell of the page's control (valid while the control is being disposed). */
  public Shell getShell() {
    Control c = getControl();
    if (c != null)
      return c.getShell();
    return container == null ? null : container.getShell();
  }

  public void setMessage(String newMessage) {
    setMessage(newMessage, NONE);
  }

  public void setMessage(String newMessage, int type) {
    message = newMessage;
    messageType = type;
    if (container != null)
      container.updateMessage();
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public int getMessageType() {
    return messageType;
  }

  public void setErrorMessage(String newMessage) {
    errorMessage = newMessage;
    if (container != null)
      container.updateMessage();
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public IPreferenceStore getPreferenceStore() {
    if (store == null)
      store = doGetPreferenceStore();
    if (store == null)
      store = PreferenceStore.getPlugin();
    return store;
  }

  protected IPreferenceStore doGetPreferenceStore() {
    return null;
  }

  public void setPreferenceStore(IPreferenceStore s) {
    store = s;
  }

  /** {@code PreferencePage.getApplyButton()}: the Apply button of the dialog that shows this page. */
  protected Button getApplyButton() {
    return container == null ? null : container.getApplyButton();
  }

  protected void updateApplyButton() {
    if (container != null)
      container.updateButtons();
  }

  protected Composite getFieldEditorParent() {
    return (Composite) control;
  }

  @Override
  public void setVisible(boolean visible) {
    if (getControl() != null && !getControl().isDisposed())
      getControl().setVisible(visible);
  }

  /** {@code DialogPage.dispose()}: frees the page's resources; its control goes with the dialog's shell. */
  public void dispose() {
  }
}
