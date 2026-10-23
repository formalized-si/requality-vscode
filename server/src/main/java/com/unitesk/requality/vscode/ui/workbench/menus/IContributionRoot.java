package com.unitesk.requality.vscode.ui.workbench.menus;

import com.unitesk.requality.vscode.ui.commands.Expression;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;

/** {@code org.eclipse.ui.menus.IContributionRoot}: the place where an extension factory adds its items. */
public interface IContributionRoot {
  /** {@code org.eclipse.ui.menus.IContributionRoot#addContributionItem}. */
  void addContributionItem(IContributionItem item, Expression visibleWhen);
}
