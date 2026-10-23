package com.unitesk.requality.vscode.ui.jface.action;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.widgets.MenuItem;

/** {@code org.eclipse.jface.action.Separator}. */
public class Separator extends ContributionItem {
  public Separator() {
  }

  public Separator(String groupName) {
    super(groupName);
  }

  @Override
  public void fill(Menu menu, int index) {
    new MenuItem(menu, SWT.SEPARATOR, index);
  }

  @Override
  public boolean isSeparator() {
    return true;
  }

  @Override
  public boolean isGroupMarker() {
    return true;
  }
}
