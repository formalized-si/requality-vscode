package com.unitesk.requality.vscode.ui.team.wizards;


import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ConfigureProjectWizardMainPage}: the repository types to share the
 * projects with, one row per configuration wizard.
 */
public class ConfigureProjectWizardMainPage extends WizardPage {
  /** {@code ConfigureProjectWizardMainPage_selectRepository}: the label above the list. */
  static final String SELECT_REPOSITORY = "Select a repository type:";

  private final java.util.List<IConfigurationElement> wizards;
  private List table;
  private IWizard selectedWizard;
  private com.unitesk.requality.vscode.ui.resources.IProject[] projects = new com.unitesk.requality.vscode.ui.resources.IProject[0];

  /** {@code ConfigureProjectWizardMainPage.setProjects}: the projects the selected wizard is initialized with. */
  public void setProjects(com.unitesk.requality.vscode.ui.resources.IProject[] projects) {
    this.projects = projects;
  }

  ConfigureProjectWizardMainPage(String pageName, String title, Object titleImage,
      java.util.List<IConfigurationElement> wizards) {
    super(pageName, title, titleImage);
    this.wizards = wizards;
  }

  /** The wizard of the selected repository type, or null. */
  public IWizard getSelectedWizard() {
    return selectedWizard;
  }

  @Override
  public boolean canFlipToNextPage() {
    return selectedWizard != null && selectedWizard.getPageCount() > 0;
  }

  @Override
  public com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage getNextPage() {
    return selectedWizard == null ? null : selectedWizard.getStartingPage();
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    setControl(composite);

    Label label = new Label(composite, SWT.LEFT);
    label.setText(SELECT_REPOSITORY);
    label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    table = new List(composite, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
    table.setLayoutData(new GridData(GridData.FILL_BOTH));
    for (IConfigurationElement e : wizards)
      table.add(e.getAttribute("name"));
    table.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectWizard(table.getSelectionIndex());
      }
    });
    setPageComplete(false);
    if (wizards.isEmpty())
      RLogger.logDebug("No repository types are contributed to Share Project", 7);
  }

  private void selectWizard(int index) {
    selectedWizard = null;
    if (index < 0 || index >= wizards.size()) {
      setPageComplete(false);
      return;
    }
    try {
      selectedWizard = ConfigureProjectWizard.createExecutableExtension(wizards.get(index), projects);
      if (selectedWizard == null) {
        setPageComplete(false);
        return;
      }
    } catch (CoreException | ClassCastException e) {
      RLogger.logError(e);
      setPageComplete(false);
      return;
    }
    selectedWizard.addPages();
    setPageComplete(true);
  }
}
