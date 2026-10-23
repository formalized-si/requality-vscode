package com.unitesk.requality.eclipse.handlers;

import org.eclipse.core.expressions.Expression;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.ui.menus.ExtensionContributionFactory;
import org.eclipse.ui.menus.IContributionRoot;
import org.eclipse.ui.services.IServiceLocator;

import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.TranserredStatusSubMenu;

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
