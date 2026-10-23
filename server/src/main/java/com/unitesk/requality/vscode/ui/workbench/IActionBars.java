package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;

/** {@code org.eclipse.ui.IActionBars}. */
public interface IActionBars {
  IMenuManager getMenuManager();

  ToolBarManager getToolBarManager();

  IAction getGlobalActionHandler(String actionId);

  void setGlobalActionHandler(String actionId, IAction handler);

  void clearGlobalActionHandlers();

  void updateActionBars();
}
