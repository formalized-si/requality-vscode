package com.unitesk.requality.vscode.ui.jface.viewers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.viewers.DialogCellEditor}: activation opens {@link #openDialogBox(Control)} directly. */
public abstract class DialogCellEditor extends CellEditor {
  private Object value;

  public DialogCellEditor() {
  }

  public DialogCellEditor(Composite parent) {
    super(parent);
  }

  public DialogCellEditor(Composite parent, int style) {
    super(parent, style);
  }

  protected abstract Object openDialogBox(Control cellEditorWindow);

  /** Called by the viewer on activation; returns the new value or null when cancelled. */
  Object runDialog(Control control) {
    return openDialogBox(control);
  }

  @Override
  protected Object doGetValue() {
    return value;
  }

  @Override
  protected void doSetValue(Object v) {
    value = v;
  }

  protected void updateContents(Object v) {
  }

  @Override
  JsonObject clientEditor() {
    return null;
  }

  @Override
  Object fromClient(JsonElement v) {
    return value;
  }
}
