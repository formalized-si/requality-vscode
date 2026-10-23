package com.unitesk.requality.vscode.ui.swt.layout;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Layout;

/** {@code org.eclipse.swt.layout.FillLayout}. */
public final class FillLayout extends Layout {
  public int type = SWT.HORIZONTAL;
  public int marginWidth = 0;
  public int marginHeight = 0;
  public int spacing = 0;

  public FillLayout() {
  }

  public FillLayout(int type) {
    this.type = type;
  }

  @Override
  public JsonObject toJson() {
    JsonObject o = new JsonObject();
    o.addProperty("type", "fill");
    o.addProperty("vertical", type == SWT.VERTICAL);
    o.addProperty("mw", marginWidth);
    o.addProperty("mh", marginHeight);
    o.addProperty("spacing", spacing);
    return o;
  }
}
