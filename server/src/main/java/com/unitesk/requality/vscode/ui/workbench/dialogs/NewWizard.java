package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.StringTokenizer;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardContainer;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.dialogs.NewWizard}: the new wizard that lets the user choose which new (nested) wizard
 * to run. The set of available new wizards comes from the {@code org.eclipse.ui.newWizards} extension point.
 */
public class NewWizard extends Wizard {
  private static final String CATEGORY_SEPARATOR = "/";

  private String categoryId;
  private NewWizardSelectionPage mainPage;
  private boolean projectsOnly;
  private IStructuredSelection selection = StructuredSelection.EMPTY;
  private IWorkbench workbench;

  /** Creates the wizard: its Back and Next buttons are always shown (the selected wizard's pages follow). */
  public NewWizard() {
    setForcePreviousAndNextButtons(true);
  }

  /** Creates the wizard pages: the selection page, which shows the categories and wizards of the registry. */
  @Override
  public void addPages() {
    IWizardCategory root = PlatformUI.getWorkbench().getNewWizardRegistry().getRootCategory();
    if (categoryId != null) {
      IWizardCategory categories = root;
      StringTokenizer familyTokenizer = new StringTokenizer(categoryId, CATEGORY_SEPARATOR);
      while (familyTokenizer.hasMoreElements()) {
        categories = getChildWithID(categories, familyTokenizer.nextToken());
        if (categories == null)
          break;
      }
      if (categories != null)
        root = categories;
    }
    mainPage = new NewWizardSelectionPage(workbench, selection, root, projectsOnly);
    addPage(mainPage);
  }

  /** The id of the category of wizards to show, or null to show all categories. */
  public String getCategoryId() {
    return categoryId;
  }

  /** Sets the id of the category of wizards to show, or null to show all categories. */
  public void setCategoryId(String id) {
    categoryId = id;
  }

  /** Sets the projects only flag: only the project wizards are shown. */
  public void setProjectsOnly(boolean b) {
    projectsOnly = b;
  }

  private static IWizardCategory getChildWithID(IWizardCategory parent, String id) {
    for (IWizardCategory wizardCategory : parent.getCategories())
      if (wizardCategory.getId().equals(id))
        return wizardCategory;
    return null;
  }

  /** Initializes the wizard with the workbench and the selection (the title of the dialog is the default one). */
  public void init(IWorkbench aWorkbench, IStructuredSelection currentSelection) {
    this.workbench = aWorkbench;
    this.selection = currentSelection == null ? StructuredSelection.EMPTY : currentSelection;
    if (getWindowTitle() == null)
      setWindowTitle(projectsOnly ? "New Project" : "New");
    setNeedsProgressMonitor(true);
  }

  /**
   * {@code NewWizard.performFinish}: Finish on the selection page finishes the selected wizard (a wizard without pages);
   * from the pages of the selected wizard, that wizard has finished first (WizardDialog finishes the nested wizards).
   */
  @Override
  public boolean performFinish() {
    if (mainPage == null)
      return true;
    if (getContainer().getCurrentPage() == mainPage) {
      if (mainPage.canFinishEarly()) {
        IWizard wizard = mainPage.getSelectedWizard();
        wizard.setContainer(getContainer());
        return wizard.performFinish();
      }
    }
    return true;
  }

  /** {@code NewWizard.canFinish}: the selection page can finish when the selected wizard has no pages. */
  @Override
  public boolean canFinish() {
    if (getContainer() != null && getContainer().getCurrentPage() == mainPage) {
      if (mainPage.canFinishEarly())
        return true;
    }
    return super.canFinish();
  }
}
