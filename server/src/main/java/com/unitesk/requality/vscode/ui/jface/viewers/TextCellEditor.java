package com.unitesk.requality.vscode.ui.jface.viewers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/** {@code org.eclipse.jface.viewers.TextCellEditor}: a String value. */
public class TextCellEditor extends CellEditor {
  private String value = "";

  public TextCellEditor() {
  }

  public TextCellEditor(Composite parent) {
    super(parent);
  }

  public TextCellEditor(Composite parent, int style) {
    super(parent, style);
  }

  @Override
  protected Object doGetValue() {
    return value;
  }

  @Override
  protected void doSetValue(Object v) {
    value = v == null ? "" : v.toString();
  }

  @Override
  JsonObject clientEditor() {
    JsonObject o = new JsonObject();
    o.addProperty("editor", "text");
    o.addProperty("value", value);
    o.addProperty("multi", SWT.has(getStyle(), SWT.MULTI));
    return o;
  }

  @Override
  Object fromClient(JsonElement v) {
    return v == null || v.isJsonNull() ? "" : v.getAsString();
  }
}
