package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

/** {@code org.eclipse.ui.actions.ContributionItemFactory}: the standard contribution items of the workbench. */
public abstract class ContributionItemFactory {
  /** {@code ContributionItemFactory.VIEWS_SHOW_IN}: the "Show In" targets of the selection (see {@link ShowInMenu}). */
  public static final ContributionItemFactory VIEWS_SHOW_IN = new ContributionItemFactory("viewsShowIn") {
    @Override
    public IContributionItem create(IWorkbenchWindow window) {
      if (window == null)
        throw new IllegalArgumentException();
      return new ShowInMenu(window, getId());
    }
  };

  private final String id;

  protected ContributionItemFactory(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  /** Creates the contribution item for the window. */
  public abstract IContributionItem create(IWorkbenchWindow window);
}
