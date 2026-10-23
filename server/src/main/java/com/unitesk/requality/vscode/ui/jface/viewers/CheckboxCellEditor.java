package com.unitesk.requality.vscode.ui.jface.viewers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/** {@code org.eclipse.jface.viewers.CheckboxCellEditor}: a Boolean value. */
public class CheckboxCellEditor extends CellEditor {
  private boolean value;

  public CheckboxCellEditor() {
  }

  public CheckboxCellEditor(Composite parent) {
    super(parent);
  }

  public CheckboxCellEditor(Composite parent, int style) {
    super(parent, style);
  }

  @Override
  protected Object doGetValue() {
    return Boolean.valueOf(value);
  }

  @Override
  protected void doSetValue(Object v) {
    value = Boolean.TRUE.equals(v);
  }

  @Override
  JsonObject clientEditor() {
    JsonObject o = new JsonObject();
    o.addProperty("editor", "check");
    o.addProperty("value", value);
    return o;
  }

  @Override
  Object fromClient(JsonElement v) {
    return v != null && !v.isJsonNull() && v.getAsBoolean();
  }
}
