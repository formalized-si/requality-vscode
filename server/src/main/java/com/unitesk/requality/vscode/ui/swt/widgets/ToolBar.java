package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code org.eclipse.swt.widgets.ToolBar}. A toolbar created with {@link #proxy()} is not rendered: the buttons of a
 * view toolbar are VS Code view/title items, so the server keeps its {@link ToolItem}s as proxies only.
 */
public class ToolBar extends Composite {
  private final List<ToolItem> items = new ArrayList<>();

  /** A rendered toolbar (a row of controls in the webview). */
  public ToolBar(Composite parent, int style) {
    super(parent, style, "composite");
    created();
  }

  private ToolBar(int style) {
    super(null, style, "composite");
  }

  /** A server-side toolbar of a view: its items are not rendered. */
  public static ToolBar proxy() {
    return new ToolBar(0);
  }

  public ToolItem[] getItems() {
    synchronized (display) {
      return items.toArray(new ToolItem[0]);
    }
  }

  void addItem(ToolItem item, int index) {
    synchronized (display) {
      if (index < 0 || index > items.size())
        items.add(item);
      else
        items.add(index, item);
    }
  }

  void removeItem(ToolItem item) {
    synchronized (display) {
      items.remove(item);
    }
  }
}
