package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.vscode.ui.commands.Expression;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.workbench.menus.ExtensionContributionFactory;
import com.unitesk.requality.vscode.ui.workbench.menus.IContributionRoot;
import com.unitesk.requality.vscode.ui.workbench.services.IServiceLocator;

import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.port.eclipse.TranserredStatusSubMenu;

public class TransferedStatusGroup extends ExtensionContributionFactory {

  public TransferedStatusGroup() {}

  @Override
  public void createContributionItems(IServiceLocator serviceLocator, IContributionRoot additions) {
    SelectionRecord nodes = SelectionManager.getOne().getSelection();
    if (nodes != null && nodes.fullSelection.size()>0)
      if (TranserredStatusSubMenu.someElementsTranferedFilter.select(nodes).fullSelection.size() > 0) {//TODO rewrite?
        MenuManager mm =
            new MenuManager("Change transferred status", "com.unitesk.requality.ChangeTransStatus");
        mm.add(new TranserredStatusSubMenu());
        additions.addContributionItem(mm, Expression.TRUE);
      }
  }
}
