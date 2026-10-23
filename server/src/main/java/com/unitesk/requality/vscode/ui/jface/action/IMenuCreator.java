package com.unitesk.requality.vscode.ui.jface.action;

import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;

/** {@code org.eclipse.jface.action.IMenuCreator}. */
public interface IMenuCreator {
  void dispose();

  Menu getMenu(Control parent);

  Menu getMenu(Menu parent);
}
