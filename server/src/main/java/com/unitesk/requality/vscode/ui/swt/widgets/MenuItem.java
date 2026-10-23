package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.MenuItem}. */
public class MenuItem extends Item {
  final Menu parent;
  private Menu menu;
  private boolean selection;
  private boolean enabled = true;

  public MenuItem(Menu parent, int style) {
    this(parent, style, -1);
  }

  public MenuItem(Menu parent, int style, int index) {
    super(parent, style, "menuitem");
    this.parent = parent;
    if (index < 0 || index > parent.items.size())
      parent.items.add(this);
    else
      parent.items.add(index, this);
    created();
  }

  @Override
  JsonObject createOp() {
    JsonObject o = super.createOp();
    o.getAsJsonObject("props").addProperty("position", parent.items.indexOf(this));
    return o;
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    return menu == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(menu);
  }

  public Menu getParent() {
    return parent;
  }

  public void setMenu(Menu menu) {
    this.menu = menu;
  }

  public Menu getMenu() {
    return menu;
  }

  public void setSelection(boolean b) {
    selection = b;
    set("selection", b);
  }

  public boolean getSelection() {
    return selection;
  }

  public void setEnabled(boolean b) {
    enabled = b;
    set("enabled", b);
  }

  public boolean getEnabled() {
    return enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setAccelerator(int accelerator) {
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
    addTyped(SWT.DefaultSelection, l);
  }

  public void removeSelectionListener(SelectionListener l) {
    removeTyped(SWT.Selection, l);
  }

  @Override
  void releaseChildren() {
    if (menu != null)
      menu.dispose();
  }

  @Override
  void releaseWidget() {
    parent.items.remove(this);
  }

  /** A chosen radio item is selected and the other radio items of its group (adjacent radio items) are not. */
  private void selectRadio() {
    java.util.List<MenuItem> siblings = parent.items;
    int index = siblings.indexOf(this);
    for (int i = index - 1; i >= 0 && SWT.has(siblings.get(i).style, SWT.RADIO); i--)
      siblings.get(i).setSelection(false);
    for (int i = index + 1; i < siblings.size() && SWT.has(siblings.get(i).style, SWT.RADIO); i++)
      siblings.get(i).setSelection(false);
    setSelection(true);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if (!"selection".equals(type) || !enabled)
      return;
    if (SWT.has(style, SWT.CHECK))
      selection = !selection;
    if (SWT.has(style, SWT.RADIO))
      selectRadio();
    notifyListeners(SWT.Selection, new Event());
  }
}
