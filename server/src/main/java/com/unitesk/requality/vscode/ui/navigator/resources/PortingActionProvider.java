package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.navigator.WizardActionGroup;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;
import com.unitesk.requality.vscode.ui.workbench.actions.ExportResourcesAction;
import com.unitesk.requality.vscode.ui.workbench.actions.ImportResourcesAction;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.PortingActionProvider}: Import... and Export... in group.port
 * of the context menu (submenus with the wizard shortcuts when the navigator content has them), and the global Import and
 * Export actions of the explorer.
 */
public class PortingActionProvider extends CommonActionProvider {
  private static final String COMMON_NAVIGATOR_IMPORT_MENU = "common.import.menu"; //$NON-NLS-1$
  private static final String COMMON_NAVIGATOR_EXPORT_MENU = "common.export.menu"; //$NON-NLS-1$

  private ImportResourcesAction importAction;
  private ExportResourcesAction exportAction;
  private WizardActionGroup importWizardActionGroup;
  private WizardActionGroup exportWizardActionGroup;
  private boolean disposed = false;
  private boolean contribute = false;

  @Override
  public void init(ICommonActionExtensionSite anExtensionSite) {
    super.init(anExtensionSite);
    if (anExtensionSite.getViewSite() != null) {
      Supplier<Shell> shell = () -> anExtensionSite.getViewSite().getShell();
      importAction = new ImportResourcesAction(shell);
      exportAction = new ExportResourcesAction(shell);
      importWizardActionGroup = new WizardActionGroup(PlatformUI.getWorkbench().getActiveWorkbenchWindow(),
          PlatformUI.getWorkbench().getImportWizardRegistry(), WizardActionGroup.TYPE_IMPORT, anExtensionSite.getContentService());
      exportWizardActionGroup = new WizardActionGroup(PlatformUI.getWorkbench().getActiveWorkbenchWindow(),
          PlatformUI.getWorkbench().getExportWizardRegistry(), WizardActionGroup.TYPE_EXPORT, anExtensionSite.getContentService());
      contribute = true;
    }
  }

  /** Extends the superclass implementation to dispose the subgroups. */
  @Override
  public void dispose() {
    if (!contribute)
      return;
    importAction = null;
    exportAction = null;
    disposed = true;
  }

  private IStructuredSelection selection() {
    ISelection s = getContext() == null ? null : getContext().getSelection();
    return s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY;
  }

  @Override
  public void fillContextMenu(IMenuManager aMenu) {
    if (!contribute)
      return;
    if (disposed)
      throw new IllegalStateException();
    ISelection selection = getContext().getSelection();
    if (!(selection instanceof IStructuredSelection) || ((IStructuredSelection) selection).size() > 1) {
      addSimplePortingMenus(aMenu);
    } else {
      addImportMenu(aMenu);
      addExportMenu(aMenu);
    }
  }

  private void addSimplePortingMenus(IMenuManager aMenu) {
    aMenu.appendToGroup(ICommonMenuConstants.GROUP_PORT, importAction);
    aMenu.appendToGroup(ICommonMenuConstants.GROUP_PORT, exportAction);
  }

  private void addImportMenu(IMenuManager aMenu) {
    importWizardActionGroup.setContext(getContext());
    if (importWizardActionGroup.getWizardActionIds().length == 0) {
      aMenu.appendToGroup(ICommonMenuConstants.GROUP_PORT, importAction);
      return;
    }
    IMenuManager submenu = new MenuManager("&Import", COMMON_NAVIGATOR_IMPORT_MENU);
    importWizardActionGroup.fillContextMenu(submenu);
    submenu.add(new Separator(ICommonMenuConstants.GROUP_ADDITIONS));
    submenu.add(new Separator());
    submenu.add(importAction);
    aMenu.appendToGroup(ICommonMenuConstants.GROUP_PORT, submenu);
  }

  private void addExportMenu(IMenuManager aMenu) {
    exportWizardActionGroup.setContext(getContext());
    if (exportWizardActionGroup.getWizardActionIds().length == 0) {
      aMenu.appendToGroup(ICommonMenuConstants.GROUP_PORT, exportAction);
      return;
    }
    IMenuManager submenu = new MenuManager("E&xport", COMMON_NAVIGATOR_EXPORT_MENU);
    exportWizardActionGroup.fillContextMenu(submenu);
    submenu.add(new Separator(ICommonMenuConstants.GROUP_ADDITIONS));
    submenu.add(new Separator());
    submenu.add(exportAction);
    aMenu.appendToGroup(ICommonMenuConstants.GROUP_PORT, submenu);
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    if (!contribute)
      return;
    updateActionBars();
    // VSCODE: the window of the IDE sets these handlers in its action builder; here the explorer's action bars do.
    actionBars.setGlobalActionHandler(ActionFactory.IMPORT.getId(), importAction);
    actionBars.setGlobalActionHandler(ActionFactory.EXPORT.getId(), exportAction);
  }

  @Override
  public void updateActionBars() {
    if (getContext() == null || !contribute)
      return;
    IStructuredSelection selection = selection();
    importAction.selectionChanged(selection);
    exportAction.selectionChanged(selection);
  }
}
