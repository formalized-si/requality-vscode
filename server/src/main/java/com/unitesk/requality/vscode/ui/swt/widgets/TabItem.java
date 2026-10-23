package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;

/** {@code org.eclipse.swt.widgets.TabItem}. */
public class TabItem extends Item {
  final TabFolder parent;
  private Control control;
  private String toolTip;

  public TabItem(TabFolder parent, int style) {
    this(parent, style, -1);
  }

  public TabItem(TabFolder parent, int style, int index) {
    super(parent, style, "tabitem");
    this.parent = parent;
    parent.addItem(this, index);
    created();
  }

  @Override
  JsonObject createOp() {
    JsonObject o = super.createOp();
    o.getAsJsonObject("props").addProperty("position", parent.items.indexOf(this));
    return o;
  }

  public TabFolder getParent() {
    return parent;
  }

  public void setControl(Control control) {
    this.control = control;
    set("control", control == null ? null : control.id);
  }

  public Control getControl() {
    return control;
  }

  public void setToolTipText(String s) {
    toolTip = s;
    set("tooltip", s);
  }

  public String getToolTipText() {
    return toolTip;
  }

  @Override
  void releaseWidget() {
    parent.removeItem(this);
  }
}
