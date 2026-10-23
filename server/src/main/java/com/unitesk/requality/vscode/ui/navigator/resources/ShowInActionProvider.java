package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.actions.ContributionItemFactory;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.ShowInActionProvider}: the "Show In" submenu of the
 * explorer, labelled with the key binding of the command.
 */
public class ShowInActionProvider extends CommonActionProvider {
  /** VSCODE: the binding of org.eclipse.ui.navigate.showInQuickMenu, as the workbench binding service would format it. */
  static final String SHOW_IN_KEY_BINDING = "Alt+Shift+W";

  @Override
  public void fillContextMenu(IMenuManager menu) {
    IContributionItem showInAction = ContributionItemFactory.VIEWS_SHOW_IN
        .create(PlatformUI.getWorkbench().getActiveWorkbenchWindow());
    MenuManager showInSubMenu = new MenuManager(getShowInMenuLabel());
    showInSubMenu.add(showInAction);
    menu.appendToGroup(ICommonMenuConstants.GROUP_OPEN, showInSubMenu);
  }

  private static String getShowInMenuLabel() {
    return "Sho&w In" + '\t' + SHOW_IN_KEY_BINDING;
  }
}
