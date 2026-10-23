package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.Arrays;
import java.util.function.Predicate;

import com.unitesk.requality.vscode.ui.ide.actions.NewExampleAction;
import com.unitesk.requality.vscode.ui.ide.actions.NewProjectAction;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.WizardActionGroup;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWizardRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.actions.NewWizardAction;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.NewActionProvider}: adds the "New" submenu after the
 * {@code group.new} group of the popup menu: the Project... shortcut and the project wizards of the commonWizard
 * contributions, the commonWizard shortcuts of the other new wizards for the selection, the Example... shortcut when
 * the examples category exists, and Other... (the new wizard selection dialog).
 */
public class NewActionProvider extends CommonActionProvider {
  private static final String FULL_EXAMPLES_WIZARD_CATEGORY = "org.eclipse.ui.Examples";
  private static final String NEW_MENU_NAME = "common.new.menu";
  private static final String PROJECT_TAG = "project";
  /** The wizards tagged as projects ({@code WorkbenchWizardElement.TAG_PROJECT}). */
  private static final Predicate<IWizardDescriptor> PROJECT_WIZARD_FILTER = wizardDescriptor -> Arrays
      .stream(wizardDescriptor.getTags()).anyMatch(PROJECT_TAG::equals);

  private NewWizardAction showDlgAction;
  private IAction newProjectAction;
  private IAction newExampleAction;
  private WizardActionGroup newWizardActionGroup;
  private boolean contribute = false;
  private WizardActionGroup newProjectWizardActionGroup;

  @Override
  public void init(ICommonActionExtensionSite anExtensionSite) {
    super.init(anExtensionSite);
    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (window == null)
      return;
    // VSCODE: the view site is always a workbench site here (a view of the workbench window).
    showDlgAction = new NewWizardAction(window);
    newProjectAction = new NewProjectAction(window);
    newExampleAction = new NewExampleAction(window);
    IWizardRegistry registry = PlatformUI.getWorkbench().getNewWizardRegistry();
    newProjectWizardActionGroup = new WizardActionGroup(window, registry, WizardActionGroup.TYPE_NEW,
        anExtensionSite.getContentService(), PROJECT_WIZARD_FILTER, false);
    newWizardActionGroup = new WizardActionGroup(window, registry, WizardActionGroup.TYPE_NEW,
        anExtensionSite.getContentService(), PROJECT_WIZARD_FILTER.negate(), true);
    contribute = true;
  }

  /**
   * Adds the "New" submenu after {@code group.new}: the project shortcuts, a separator, the other shortcuts, a separator
   * and the Other... item, with the Example... item before them when the examples category exists.
   */
  @Override
  public void fillContextMenu(IMenuManager menu) {
    IMenuManager submenu = new MenuManager("&New", NEW_MENU_NAME);
    if (!contribute)
      return;
    submenu.add(newProjectAction);
    newProjectWizardActionGroup.setContext(getContext());
    newProjectWizardActionGroup.fillContextMenu(submenu);
    submenu.add(new Separator());
    newWizardActionGroup.setContext(getContext());
    newWizardActionGroup.fillContextMenu(submenu);
    submenu.add(new Separator(ICommonMenuConstants.GROUP_ADDITIONS));
    if (hasExamples()) {
      submenu.add(new Separator());
      submenu.add(newExampleAction);
    }
    submenu.add(new Separator());
    submenu.add(showDlgAction);
    menu.insertAfter(ICommonMenuConstants.GROUP_NEW, submenu);
  }

  /** Whether the examples category exists in the new wizard registry. */
  private boolean hasExamples() {
    IWizardCategory category = PlatformUI.getWorkbench().getNewWizardRegistry().findCategory(FULL_EXAMPLES_WIZARD_CATEGORY);
    return category != null;
  }

  @Override
  public void dispose() {
    showDlgAction = null;
    super.dispose();
  }
}
