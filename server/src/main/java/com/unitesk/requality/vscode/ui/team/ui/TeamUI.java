package com.unitesk.requality.vscode.ui.team.ui;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.team.history.GenericHistoryView;
import com.unitesk.requality.vscode.ui.team.registry.TeamContentProviderManager;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryPageSource;
import com.unitesk.requality.vscode.ui.team.ui.mapping.ITeamContentProviderManager;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryView;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeManager;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeManager;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;

/** {@code org.eclipse.team.ui.TeamUI}: the entry points of the team UI (the History view). */
public final class TeamUI {
  private TeamUI() {
  }

  /** {@code TeamUI.getTeamContentProviderManager()}: the team content providers of the platform (the Models page). */
  public static ITeamContentProviderManager getTeamContentProviderManager() {
    return TeamContentProviderManager.getInstance();
  }

  /** {@code TeamUI.getSynchronizeManager()}: the synchronize participants shown in the Synchronize view. */
  public static ISynchronizeManager getSynchronizeManager() {
    return SynchronizeManager.getInstance();
  }

  /**
   * {@code TeamUI.showHistoryFor(page, object, pageSource)}: shows the history of the object in the History view of the
   * page (a new view when the one of the page is pinned), with the page of the page source. Returns the view, or null.
   */
  public static IHistoryView showHistoryFor(IWorkbenchPage page, Object input, IHistoryPageSource pageSource) {
    try {
      IViewPart part = page.findView(IHistoryView.VIEW_ID);
      if (part == null)
        part = page.showView(IHistoryView.VIEW_ID);
      if (!(part instanceof GenericHistoryView view))
        return null;
      view.showHistoryPageFor(input, true, false, pageSource);
      return view;
    } catch (PartInitException e) {
      RLogger.logError(e);
      return null;
    }
  }
}
