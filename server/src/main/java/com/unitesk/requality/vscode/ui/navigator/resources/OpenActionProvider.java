package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.action.GroupMarker;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionConstants;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.actions.OpenFileAction;
import com.unitesk.requality.vscode.ui.workbench.actions.OpenWithMenu;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.OpenActionProvider}: "Open" of the selected files (the
 * global action {@link ICommonActionConstants#OPEN}) and the "Open With" submenu of a file.
 */
public class OpenActionProvider extends CommonActionProvider {
  private OpenFileAction openFileAction;

  // VSCODE: the explorer is in the workbench's window and page; the Eclipse ICommonViewerWorkbenchSite is not ported.
  @Override
  public void init(ICommonActionExtensionSite aConfig) {
  }

  private IWorkbenchPage page() {
    return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
  }

  private OpenFileAction openFileAction() {
    if (openFileAction == null)
      openFileAction = new OpenFileAction(page());
    return openFileAction;
  }

  @Override
  public void fillContextMenu(IMenuManager aMenu) {
    if (getContext() == null || !(getContext().getSelection() instanceof IStructuredSelection)
        || getContext().getSelection().isEmpty())
      return;
    IStructuredSelection selection = (IStructuredSelection) getContext().getSelection();
    openFileAction().selectionChanged(selection);
    if (openFileAction().isEnabled())
      aMenu.insertAfter(ICommonMenuConstants.GROUP_OPEN, openFileAction());
    addOpenWithMenu(aMenu);
  }

  @Override
  public void fillActionBars(IActionBars theActionBars) {
    if (getContext() == null || !(getContext().getSelection() instanceof IStructuredSelection))
      return;
    IStructuredSelection selection = (IStructuredSelection) getContext().getSelection();
    if (selection.size() == 1 && selection.getFirstElement() instanceof IFile) {
      openFileAction().selectionChanged(selection);
      theActionBars.setGlobalActionHandler(ICommonActionConstants.OPEN, openFileAction());
    }
  }

  private void addOpenWithMenu(IMenuManager aMenu) {
    IStructuredSelection ss = (IStructuredSelection) getContext().getSelection();
    if (ss == null || ss.size() != 1)
      return;
    Object o = ss.getFirstElement();
    if (!(o instanceof IFile))
      return;
    // Create a menu flyout.
    MenuManager submenu = new MenuManager("Open With", ICommonMenuConstants.GROUP_OPEN_WITH);
    submenu.add(new GroupMarker(ICommonMenuConstants.GROUP_TOP));
    submenu.add(new OpenWithMenu(page(), (IFile) o));
    submenu.add(new GroupMarker(ICommonMenuConstants.GROUP_ADDITIONS));
    // Add the submenu when it has items.
    if (submenu.getItems().length > 2 && submenu.isEnabled())
      aMenu.appendToGroup(ICommonMenuConstants.GROUP_OPEN_WITH, submenu);
  }
}
