package com.unitesk.requality.vscode.ui.swt.layout;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Layout;

/** {@code org.eclipse.swt.layout.GridLayout} with the SWT defaults. */
public final class GridLayout extends Layout {
  public int numColumns = 1;
  public boolean makeColumnsEqualWidth = false;
  public int marginWidth = 5;
  public int marginHeight = 5;
  public int marginLeft = 0;
  public int marginTop = 0;
  public int marginRight = 0;
  public int marginBottom = 0;
  public int horizontalSpacing = 5;
  public int verticalSpacing = 5;

  public GridLayout() {
  }

  public GridLayout(int numColumns, boolean makeColumnsEqualWidth) {
    this.numColumns = numColumns;
    this.makeColumnsEqualWidth = makeColumnsEqualWidth;
  }

  @Override
  public JsonObject toJson() {
    JsonObject o = new JsonObject();
    o.addProperty("type", "grid");
    o.addProperty("numColumns", numColumns);
    o.addProperty("equal", makeColumnsEqualWidth);
    o.addProperty("mt", marginHeight + marginTop);
    o.addProperty("mb", marginHeight + marginBottom);
    o.addProperty("ml", marginWidth + marginLeft);
    o.addProperty("mr", marginWidth + marginRight);
    o.addProperty("hs", horizontalSpacing);
    o.addProperty("vs", verticalSpacing);
    return o;
  }
}
