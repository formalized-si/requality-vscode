package com.unitesk.requality.vscode.ui.jface.wizard;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.jface.wizard.Wizard}. */
public abstract class Wizard implements IWizard {
  private final List<IWizardPage> pages = new ArrayList<>();
  private IWizardContainer container;
  private String windowTitle;
  private boolean forcePreviousAndNextButtons;
  private boolean needsProgressMonitor;

  private com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings dialogSettings;

  public com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings getDialogSettings() {
    return dialogSettings;
  }

  public void setDialogSettings(com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings settings) {
    dialogSettings = settings;
  }

  public void addPage(IWizardPage page) {
    pages.add(page);
    page.setWizard(this);
  }

  @Override
  public void addPages() {
  }

  @Override
  public boolean canFinish() {
    for (IWizardPage p : pages)
      if (!p.isPageComplete())
        return false;
    return true;
  }

  @Override
  public void createPageControls(Composite pageContainer) {
    for (IWizardPage p : pages)
      if (p.getControl() == null)
        p.createControl(pageContainer);
  }

  @Override
  public IWizardContainer getContainer() {
    return container;
  }

  @Override
  public void setContainer(IWizardContainer c) {
    container = c;
  }

  public Shell getShell() {
    return container == null ? null : container.getShell();
  }

  @Override
  public IWizardPage getNextPage(IWizardPage page) {
    int i = pages.indexOf(page);
    return i < 0 || i == pages.size() - 1 ? null : pages.get(i + 1);
  }

  @Override
  public IWizardPage getPreviousPage(IWizardPage page) {
    int i = pages.indexOf(page);
    return i <= 0 ? null : pages.get(i - 1);
  }

  @Override
  public IWizardPage getPage(String name) {
    for (IWizardPage p : pages)
      if (p.getName().equals(name))
        return p;
    return null;
  }

  @Override
  public int getPageCount() {
    return pages.size();
  }

  @Override
  public IWizardPage[] getPages() {
    return pages.toArray(new IWizardPage[0]);
  }

  @Override
  public IWizardPage getStartingPage() {
    return pages.isEmpty() ? null : pages.get(0);
  }

  @Override
  public String getWindowTitle() {
    return windowTitle;
  }

  public void setWindowTitle(String t) {
    windowTitle = t;
    if (container != null)
      container.updateWindowTitle();
  }

  public void setForcePreviousAndNextButtons(boolean b) {
    forcePreviousAndNextButtons = b;
  }

  @Override
  public boolean needsPreviousAndNextButtons() {
    return forcePreviousAndNextButtons || pages.size() > 1;
  }

  public void setNeedsProgressMonitor(boolean b) {
    needsProgressMonitor = b;
  }

  public boolean needsProgressMonitor() {
    return needsProgressMonitor;
  }

  public void setDefaultPageImageDescriptor(Object o) {
  }

  public void setHelpAvailable(boolean b) {
  }

  @Override
  public boolean performCancel() {
    return true;
  }

  @Override
  public abstract boolean performFinish();

  @Override
  public void dispose() {
    for (IWizardPage p : pages)
      p.dispose();
  }
}
