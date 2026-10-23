package com.unitesk.requality.vscode.ui.jface.action;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.RowLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolBar;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolItem;

/**
 * {@code org.eclipse.jface.action.ToolBarManager}: a row of buttons. Without {@link #createControl} the toolbar is the
 * server-side proxy of a view toolbar ({@link #getControl()}): its items are the VS Code view/title buttons.
 */
public class ToolBarManager {
  private final List<IContributionItem> items = new ArrayList<>();
  private ToolBar bar;
  private boolean rendered;

  public ToolBarManager() {
  }

  public ToolBarManager(int style) {
  }

  public void add(IAction action) {
    add(new ActionContributionItem(action));
  }

  public void add(IContributionItem item) {
    items.add(item);
    if (bar != null && !rendered)
      update(true);
  }

  public Control createControl(Composite parent) {
    rendered = true;
    bar = new ToolBar(parent, SWT.NONE);
    RowLayout rl = new RowLayout(SWT.HORIZONTAL);
    rl.marginTop = rl.marginBottom = rl.marginLeft = rl.marginRight = 0;
    bar.setLayout(rl);
    update(true);
    return bar;
  }

  /** The toolbar: the rendered one of {@link #createControl}, else the server-side proxy of the view toolbar. */
  public ToolBar getControl() {
    if (bar == null) {
      rendered = false;
      bar = ToolBar.proxy();
      update(true);
    }
    return bar;
  }

  public void update(boolean force) {
    if (bar == null || bar.isDisposed())
      return;
    if (!rendered) {
      for (ToolItem ti : bar.getItems())
        ti.dispose();
      for (IContributionItem item : items)
        if (item.isVisible() && !item.isSeparator())
          item.fill(bar, -1);
      return;
    }
    for (Control c : bar.getChildren())
      c.dispose();
    for (IContributionItem item : items)
      if (item.isVisible() && !item.isSeparator())
        item.fill(bar);
  }

  public void removeAll() {
    items.clear();
  }

  public void dispose() {
    if (bar != null && !bar.isDisposed())
      bar.dispose();
  }
}
