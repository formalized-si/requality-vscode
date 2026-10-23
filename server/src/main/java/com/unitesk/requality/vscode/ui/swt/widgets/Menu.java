package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.MenuListener;

/** {@code org.eclipse.swt.widgets.Menu}: pop-up menus shown by the renderer at the click position. */
public class Menu extends Widget {
  final Control owner;
  final MenuItem parentItem;
  final java.util.List<MenuItem> items = new ArrayList<>();
  private boolean visible;

  public Menu(Control parent) {
    super(parent, SWT.POP_UP, "menu");
    owner = parent;
    parentItem = null;
    parent.menus.add(this);
    created();
  }

  public Menu(Shell parent, int style) {
    this((Control) parent);
  }

  public Menu(Menu parentMenu) {
    super(parentMenu, SWT.DROP_DOWN, "menu");
    owner = parentMenu.owner;
    parentItem = null;
    created();
  }

  public Menu(MenuItem parentItem) {
    super(parentItem, SWT.DROP_DOWN, "menu");
    owner = parentItem.parent.owner;
    this.parentItem = parentItem;
    created();
  }

  @Override
  Widget getParentWidget() {
    return parentItem != null ? parentItem : owner;
  }

  @Override
  Shell getShellInternal() {
    return owner.getShellInternal();
  }

  /** {@code Menu#getShell()}: the shell of the control the menu belongs to. */
  public Shell getShell() {
    return owner.getShell();
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    return new ArrayList<>(items);
  }

  public MenuItem[] getItems() {
    return items.toArray(new MenuItem[0]);
  }

  public MenuItem getItem(int i) {
    return items.get(i);
  }

  public int getItemCount() {
    return items.size();
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
    if (visible)
      showAt(0, 0);
  }

  public boolean isVisible() {
    return visible;
  }

  void showAt(int x, int y) {
    notifyListeners(SWT.Show, new Event());
    call("show", new int[] { x, y });
  }

  public void setLocation(int x, int y) {
  }

  public void addMenuListener(MenuListener l) {
    addTyped(SWT.Show, l);
    addTyped(SWT.Hide, l);
  }

  public void removeMenuListener(MenuListener l) {
    removeTyped(SWT.Show, l);
    removeTyped(SWT.Hide, l);
  }

  @Override
  void releaseChildren() {
    for (MenuItem i : getItems())
      i.dispose();
  }

  @Override
  void releaseWidget() {
    owner.menus.remove(this);
  }
}
