package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;

/**
 * {@code org.eclipse.ui.actions.CompoundContributionItem}: a dynamic menu section. In VS Code its items are listed
 * by the server's MenuService ({@code menu/items}) and shown in a quick pick.
 */
public abstract class CompoundContributionItem extends ContributionItem {
  protected CompoundContributionItem() {
  }

  protected CompoundContributionItem(String id) {
    super(id);
  }

  protected abstract IContributionItem[] getContributionItems();

  /** The current items (MenuService). */
  public IContributionItem[] items() {
    return getContributionItems();
  }

  @Override
  public boolean isDynamic() {
    return true;
  }

  @Override
  public void fill(Menu menu, int index) {
    for (IContributionItem item : getContributionItems())
      item.fill(menu, -1);
  }

  @Override
  public void fill(Composite parent) {
  }
}
