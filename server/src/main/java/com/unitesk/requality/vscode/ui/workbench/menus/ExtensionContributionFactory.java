package com.unitesk.requality.vscode.ui.workbench.menus;

import com.unitesk.requality.vscode.ui.workbench.services.IServiceLocator;

/** {@code org.eclipse.ui.menus.ExtensionContributionFactory}: adds dynamic items to a menu contribution root. */
public abstract class ExtensionContributionFactory {
  public abstract void createContributionItems(IServiceLocator serviceLocator, IContributionRoot additions);
}
