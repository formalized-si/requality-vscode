package com.unitesk.requality.vscode.ui.workbench;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;

/**
 * Action bars of a server-hosted part. Global action handlers (undo, redo, copy, delete...) are looked up by the
 * matching VS Code commands when the part is active.
 */
public class ActionBars implements IActionBars {
  private final MenuManager menu = new MenuManager();
  private final ToolBarManager toolbar = new ToolBarManager();
  private final Map<String, IAction> global = new HashMap<>();
  /** The view whose plugin.xml toolbar contributions fill the toolbar (null for none). */
  private final String viewId;
  private boolean toolbarContributed;

  public ActionBars() {
    this(null);
  }

  /** The action bars of a view: its plugin.xml toolbar contributions are added when the toolbar is first asked for. */
  public ActionBars(String viewId) {
    this.viewId = viewId;
  }

  @Override
  public IMenuManager getMenuManager() {
    return menu;
  }

  @Override
  public synchronized ToolBarManager getToolBarManager() {
    if (!toolbarContributed && viewId != null) {
      toolbarContributed = true;
      ViewToolbarContributions.fill(viewId, toolbar);
    }
    return toolbar;
  }

  @Override
  public synchronized IAction getGlobalActionHandler(String actionId) {
    return global.get(actionId);
  }

  @Override
  public synchronized void setGlobalActionHandler(String actionId, IAction handler) {
    if (handler == null)
      global.remove(actionId);
    else
      global.put(actionId, handler);
  }

  @Override
  public synchronized void clearGlobalActionHandlers() {
    global.clear();
  }

  @Override
  public void updateActionBars() {
  }
}
