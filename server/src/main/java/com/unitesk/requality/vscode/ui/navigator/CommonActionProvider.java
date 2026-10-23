package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionGroup;

/**
 * {@code org.eclipse.ui.navigator.CommonActionProvider}: an action group of a navigator content extension
 * (plugin.xml {@code actionProvider}), created and driven by the {@link NavigatorActionService}.
 */
public abstract class CommonActionProvider extends ActionGroup {
  private ICommonActionExtensionSite actionSite;

  /** Called by the NavigatorActionService: the site is known before {@link #init} runs, as in Eclipse. */
  final void internalInit(ICommonActionExtensionSite aSite) {
    actionSite = aSite;
    init(aSite);
  }

  public void init(ICommonActionExtensionSite aSite) {
    actionSite = aSite;
  }

  protected final ICommonActionExtensionSite getActionSite() {
    return actionSite;
  }

  public void restoreState(IMemento aMemento) {
  }

  public void saveState(IMemento aMemento) {
  }
}
