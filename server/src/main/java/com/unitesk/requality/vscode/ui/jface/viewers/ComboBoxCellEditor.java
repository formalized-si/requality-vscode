package com.unitesk.requality.vscode.ui.jface.viewers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/** {@code org.eclipse.jface.viewers.ComboBoxCellEditor}: the value is the Integer index of the chosen item. */
public class ComboBoxCellEditor extends CellEditor {
  public static final int DROP_DOWN_ON_MOUSE_ACTIVATION = 1;
  public static final int DROP_DOWN_ON_KEY_ACTIVATION = 1 << 1;
  public static final int DROP_DOWN_ON_PROGRAMMATIC_ACTIVATION = 1 << 2;
  public static final int DROP_DOWN_ON_TRAVERSE_ACTIVATION = 1 << 3;
  private int activationStyle;

  public void setActivationStyle(int style) {
    activationStyle = style;
  }

  public int getActivationStyle() {
    return activationStyle;
  }

  private String[] items;
  private int selection = -1;

  public ComboBoxCellEditor() {
    items = new String[0];
  }

  public ComboBoxCellEditor(Composite parent, String[] items) {
    this(parent, items, 0);
  }

  public ComboBoxCellEditor(Composite parent, String[] items, int style) {
    super(parent, style);
    this.items = items == null ? new String[0] : items;
  }

  public String[] getItems() {
    return items;
  }

  public void setItems(String[] items) {
    this.items = items == null ? new String[0] : items;
  }

  @Override
  protected Object doGetValue() {
    return Integer.valueOf(selection);
  }

  @Override
  protected void doSetValue(Object v) {
    selection = v instanceof Integer ? (Integer) v : -1;
  }

  @Override
  JsonObject clientEditor() {
    JsonObject o = new JsonObject();
    o.addProperty("editor", "combo");
    o.addProperty("value", selection);
    o.add("items", JsonRpcConnection.toJson(items));
    return o;
  }

  @Override
  Object fromClient(JsonElement v) {
    return v == null || v.isJsonNull() ? Integer.valueOf(-1) : Integer.valueOf(v.getAsInt());
  }
}
