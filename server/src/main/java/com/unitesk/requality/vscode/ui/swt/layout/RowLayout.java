package com.unitesk.requality.vscode.ui.swt.layout;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Layout;

/** {@code org.eclipse.swt.layout.RowLayout}. */
public final class RowLayout extends Layout {
  public int type = SWT.HORIZONTAL;
  public int spacing = 3;
  public boolean wrap = true;
  public boolean pack = true;
  public boolean fill = false;
  public boolean center = false;
  public boolean justify = false;
  public int marginLeft = 3;
  public int marginTop = 3;
  public int marginRight = 3;
  public int marginBottom = 3;
  public int marginWidth = 0;
  public int marginHeight = 0;

  public RowLayout() {
  }

  public RowLayout(int type) {
    this.type = type;
  }

  @Override
  public JsonObject toJson() {
    JsonObject o = new JsonObject();
    o.addProperty("type", "row");
    o.addProperty("vertical", type == SWT.VERTICAL);
    o.addProperty("spacing", spacing);
    o.addProperty("wrap", wrap);
    o.addProperty("fill", fill);
    o.addProperty("center", center);
    o.addProperty("justify", justify);
    o.addProperty("mt", marginTop + marginHeight);
    o.addProperty("mb", marginBottom + marginHeight);
    o.addProperty("ml", marginLeft + marginWidth);
    o.addProperty("mr", marginRight + marginWidth);
    return o;
  }
}
