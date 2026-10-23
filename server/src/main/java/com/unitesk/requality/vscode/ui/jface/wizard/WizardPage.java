package com.unitesk.requality.vscode.ui.jface.wizard;

import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.wizard.WizardPage}. */
public abstract class WizardPage implements IWizardPage, IMessageProvider {
  private final String name;
  private String title;
  private String description;
  private String message;
  private int messageType;
  private String errorMessage;
  private boolean pageComplete = true;
  private IWizard wizard;
  private IWizardPage previousPage;
  private Control control;

  protected WizardPage(String pageName) {
    this.name = pageName;
  }

  protected WizardPage(String pageName, String title, Object titleImage) {
    this.name = pageName;
    this.title = title;
  }

  @Override
  public boolean canFlipToNextPage() {
    return isPageComplete() && getNextPage() != null;
  }

  @Override
  public Control getControl() {
    return control;
  }

  protected void setControl(Control c) {
    control = c;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
    if (isCurrentPage())
      getContainer().updateTitleBar();
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
    if (isCurrentPage())
      getContainer().updateTitleBar();
  }

  public void setImageDescriptor(Object desc) {
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String newMessage) {
    errorMessage = newMessage;
    if (isCurrentPage())
      getContainer().updateMessage();
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public int getMessageType() {
    return messageType;
  }

  public void setMessage(String newMessage) {
    setMessage(newMessage, NONE);
  }

  public void setMessage(String newMessage, int type) {
    message = newMessage;
    messageType = type;
    if (isCurrentPage())
      getContainer().updateMessage();
  }

  @Override
  public IWizardPage getNextPage() {
    return wizard == null ? null : wizard.getNextPage(this);
  }

  @Override
  public IWizardPage getPreviousPage() {
    if (previousPage != null)
      return previousPage;
    return wizard == null ? null : wizard.getPreviousPage(this);
  }

  @Override
  public void setPreviousPage(IWizardPage page) {
    previousPage = page;
  }

  /** The dialog settings of the wizard (Wizard.getDialogSettings), or null. */
  protected com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings getDialogSettings() {
    IWizard w = getWizard();
    return w instanceof Wizard ? ((Wizard) w).getDialogSettings() : null;
  }

  @Override
  public IWizard getWizard() {
    return wizard;
  }

  @Override
  public void setWizard(IWizard newWizard) {
    wizard = newWizard;
  }

  protected IWizardContainer getContainer() {
    return wizard == null ? null : wizard.getContainer();
  }

  /** {@code DialogPage.getShell()}: the shell of the page's control, else the container's shell. */
  public Shell getShell() {
    if (getControl() != null)
      return getControl().getShell();
    IWizardContainer c = getContainer();
    return c == null ? null : c.getShell();
  }

  protected boolean isCurrentPage() {
    return getContainer() != null && getContainer().getCurrentPage() == this;
  }

  @Override
  public boolean isPageComplete() {
    return pageComplete;
  }

  public void setPageComplete(boolean complete) {
    pageComplete = complete;
    if (isCurrentPage())
      getContainer().updateButtons();
  }

  @Override
  public void setVisible(boolean visible) {
    if (control != null && !control.isDisposed())
      control.setVisible(visible);
  }

  @Override
  /** {@code DialogPage.dispose()}: frees the page's resources; its control goes with the dialog's shell. */
  public void dispose() {
  }
}
