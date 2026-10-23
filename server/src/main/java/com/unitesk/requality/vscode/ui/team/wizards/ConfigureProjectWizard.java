package com.unitesk.requality.vscode.ui.team.wizards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.ui.IConfigurationWizard;
import com.unitesk.requality.vscode.ui.team.ui.IConfigurationWizardExtension;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ConfigureProjectWizard}: Share Project. The repository types are the
 * {@code wizard} elements of the {@code configurationWizards} of org.eclipse.team.ui. None is contributed by the
 * Requality product, so the wizard lists none: the main page is empty and the wizard cannot finish.
 * A single repository type with pages is opened directly (shareProjects). The wizards filtered out by activities
 * (WorkbenchActivityHelper.filterItem) are none: the product defines no activities.
 */
public class ConfigureProjectWizard extends Wizard {
  static final String PT_CONFIGURATION = "configurationWizards";
  static final String TAG_WIZARD = "wizard";
  static final String ATT_NAME = "name";

  protected IProject[] projects;
  protected ConfigureProjectWizardMainPage mainPage;

  private ConfigureProjectWizard(IProject[] projects) {
    this.projects = projects;
    setNeedsProgressMonitor(true);
    setWindowTitle("Share Project");
  }

  @Override
  public void addPages() {
    mainPage = new ConfigureProjectWizardMainPage("configurePage1", "Share Project", null, getAvailableWizards());
    mainPage.setDescription("Select the repository plug-in that will be used to share the selected project.");
    mainPage.setProjects(projects);
    addPage(mainPage);
  }

  @Override
  public boolean canFinish() {
    if (getContainer().getCurrentPage() == mainPage) {
      return mainPage.getSelectedWizard() != null && mainPage.getNextPage() == null;
    }
    return super.canFinish();
  }

  @Override
  public boolean performFinish() {
    if (getContainer().getCurrentPage() == mainPage) {
      IWizard noPageWizard = mainPage.getSelectedWizard();
      if (noPageWizard != null && noPageWizard.canFinish())
        return noPageWizard.performFinish();
    }
    return true;
  }

  /**
   * {@code ConfigureProjectWizard.shareProjects}: the wizard of the projects, in a dialog. If only one wizard is
   * registered, that wizard is used unless it doesn't have any pages.
   */
  public static void shareProjects(Shell shell, IProject[] projects) {
    IWizard wizard = null;
    List<IConfigurationElement> wizards = getAvailableWizards();
    if (wizards.size() == 1) {
      IConfigurationElement element = wizards.get(0);
      if (wizardHasPages(element, projects)) {
        try {
          wizard = createExecutableExtension(element, projects);
        } catch (CoreException e) {
          // Log the exception and fall through to show the wizard
          RLogger.logError(e);
        }
      }
    }
    if (wizard == null) {
      wizard = new ConfigureProjectWizard(projects);
      ((ConfigureProjectWizard) wizard).setForcePreviousAndNextButtons(true);
    }
    openWizard(shell, wizard);
  }

  /**
   * {@code ConfigurationWizardElement.createExecutableExtension(IProject[])}: the wizard of the repository type,
   * initialized with the projects (a wizard without the extension shares one project; VSCODE: null for several, the
   * ConfigureMultipleProjectsWizard of Eclipse is used only by repository providers).
   */
  public static IWizard createExecutableExtension(IConfigurationElement element, IProject[] projects) throws CoreException {
    IWorkbench workbench = PlatformUI.getWorkbench();
    Object created = element.createExecutableExtension("class");
    if (created instanceof IConfigurationWizardExtension extension) {
      extension.init(workbench, projects);
      return (IWizard) created;
    }
    if (created instanceof IConfigurationWizard wizard) {
      if (projects.length != 1) {
        wizard.dispose();
        return null;
      }
      wizard.init(workbench, projects[0]);
      return wizard;
    }
    return created instanceof IWizard ? (IWizard) created : null;
  }

  /** {@code ConfigurationWizardElement.wizardHasPages}: whether the wizard of the repository type has pages. */
  public static boolean wizardHasPages(IConfigurationElement element, IProject[] projects) {
    try {
      IWizard wizard = createExecutableExtension(element, projects);
      if (wizard == null)
        return false;
      try {
        wizard.addPages();
        return wizard.getPageCount() > 0;
      } finally {
        wizard.dispose();
      }
    } catch (CoreException | RuntimeException e) {
      RLogger.logError(e);
    }
    return false;
  }

  /** {@code ConfigureProjectWizard.openWizard}: the wizard in a resizable dialog. */
  public static void openWizard(Shell shell, IWizard wizard) {
    new ResizeWizardDialog(shell, wizard).open();
  }

  private static class ResizeWizardDialog extends WizardDialog {
    ResizeWizardDialog(Shell parentShell, IWizard newWizard) {
      super(parentShell, newWizard);
      setShellStyle(getShellStyle() | SWT.RESIZE);
    }
  }

  /** {@code ConfigureProjectWizard.getAvailableWizards}: the named wizard elements, sorted by name. */
  static List<IConfigurationElement> getAvailableWizards() {
    List<IConfigurationElement> result = new ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.team.ui", PT_CONFIGURATION))
      if (TAG_WIZARD.equals(e.getName()) && e.getAttribute(ATT_NAME) != null)
        result.add(e);
    result.sort(Comparator.comparing(e -> e.getAttribute(ATT_NAME).toLowerCase()));
    return result;
  }
}
