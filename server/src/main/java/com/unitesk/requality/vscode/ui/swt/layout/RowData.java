package com.unitesk.requality.vscode.ui.swt.layout;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;

/** {@code org.eclipse.swt.layout.RowData}. */
public final class RowData {
  public int width = SWT.DEFAULT;
  public int height = SWT.DEFAULT;
  public boolean exclude = false;

  public RowData() {
  }

  public RowData(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public JsonObject toJson() {
    JsonObject o = new JsonObject();
    o.addProperty("type", "row");
    if (width != SWT.DEFAULT)
      o.addProperty("w", width);
    if (height != SWT.DEFAULT)
      o.addProperty("h", height);
    if (exclude)
      o.addProperty("exclude", true);
    return o;
  }
}
