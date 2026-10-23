package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.TreeColumn}. */
public class TreeColumn extends Item {
  final Tree parent;
  private int width;

  public TreeColumn(Tree parent, int style) {
    this(parent, style, -1);
  }

  public TreeColumn(Tree parent, int style, int index) {
    super(parent, style, "column");
    this.parent = parent;
    parent.addColumn(this, index);
    created();
  }

  @Override
  JsonObject createOp() {
    JsonObject o = super.createOp();
    o.getAsJsonObject("props").addProperty("position", parent.columns.indexOf(this));
    return o;
  }

  public Tree getParent() {
    return parent;
  }

  public void setWidth(int width) {
    this.width = width;
    set("width", width);
  }

  public int getWidth() {
    return width;
  }

  public void setAlignment(int a) {
    set("align", a == SWT.RIGHT ? "right" : a == SWT.CENTER ? "center" : "left");
  }

  public void setResizable(boolean b) {
  }

  public void setMoveable(boolean b) {
  }

  public void setToolTipText(String s) {
    set("tooltip", s);
  }

  public void pack() {
    set("width", null);
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
  }

  @Override
  void releaseWidget() {
    parent.removeColumn(this);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if ("selection".equals(type))
      notifyListeners(SWT.Selection, new Event());
  }
}
