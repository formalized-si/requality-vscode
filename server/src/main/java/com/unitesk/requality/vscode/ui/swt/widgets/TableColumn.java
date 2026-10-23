package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.TableColumn}. */
public class TableColumn extends Item {
  final Table parent;
  private int width = 0;
  private int alignment = SWT.LEFT;
  private boolean resizable = true;
  private boolean moveable;
  private String toolTip;

  public TableColumn(Table parent, int style) {
    this(parent, style, -1);
  }

  public TableColumn(Table parent, int style, int index) {
    super(parent, style, "column");
    this.parent = parent;
    parent.addColumn(this, index);
    
    if (SWT.has(style, SWT.RIGHT))
      alignment = SWT.RIGHT;
    else if (SWT.has(style, SWT.CENTER))
      alignment = SWT.CENTER;
    created();
  }

  public Table getParent() {
    return parent;
  }

  public void setWidth(int width) {
    this.width = width;
    set("width", width);
  }

  public int getWidth() {
    return width;
  }

  public void setAlignment(int alignment) {
    this.alignment = alignment;
    set("align", alignment == SWT.RIGHT ? "right" : alignment == SWT.CENTER ? "center" : "left");
  }

  public int getAlignment() {
    return alignment;
  }

  public void setResizable(boolean b) {
    resizable = b;
  }

  public boolean getResizable() {
    return resizable;
  }

  public void setMoveable(boolean b) {
    moveable = b;
  }

  public boolean getMoveable() {
    return moveable;
  }

  public void setToolTipText(String s) {
    toolTip = s;
    set("tooltip", s);
  }

  public String getToolTipText() {
    return toolTip;
  }

  /** Extension for JFace ColumnWeightData: relative width. */
  public void setWeight(int weight) {
    set("weight", weight);
  }

  public void pack() {
    set("width", null);
    width = 0;
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
    addTyped(SWT.DefaultSelection, l);
  }

  public void removeSelectionListener(SelectionListener l) {
    removeTyped(SWT.Selection, l);
    removeTyped(SWT.DefaultSelection, l);
  }

  @Override
  JsonObject createOp() {
    JsonObject o = super.createOp();
    o.getAsJsonObject("props").addProperty("position", parent.columns.indexOf(this));
    return o;
  }

  @Override
  void releaseWidget() {
    parent.removeColumn(this);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if ("selection".equals(type))
      notifyListeners(SWT.Selection, new Event());
    else if ("resize".equals(type) && d.has("width"))
      width = d.get("width").getAsInt();
  }
}
