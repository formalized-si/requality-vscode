package com.unitesk.requality.vscode.port.eclipse.views.actions;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.create.CreateElementOperation;

public class NewTypedMenuPoint extends MenuManager {

  private CreateElementOperation neh;
  private TreeNode sel;
  private String tp;

  public NewTypedMenuPoint(TreeNode db, CreateElementOperation neh, String type) {
    super(type + "...");
    this.sel = db;
    this.neh = neh;
    this.tp = type;
  }

  @Override
  public IContributionItem[] getItems() {
    List<IContributionItem> items = new ArrayList<>();
    String type = tp;
    items.add(
        new ActionContributionItem(
            new NewTypedTemplatedMenuPoint(sel, type, TreeDB.EMPTY_TEMPLATE, neh)));
    for (TreeNode s : sel.getTreeDB().getTemplates())
      if (type.equals(s.getType()))
        items.add(
            new ActionContributionItem(new NewTypedTemplatedMenuPoint(sel, type, s.getId(), neh)));
    if (items.size() == 1) return new IContributionItem[0];
    return items.toArray(new IContributionItem[0]);
  }
}
