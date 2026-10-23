package com.unitesk.requality.vscode.ui.jface.viewers;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * The control of a {@link CellEditor}. The editing field itself is drawn inline by the webview when the cell is
 * edited; key and focus events of that field are delivered to this control (with the text typed so far), so listeners
 * that Eclipse code adds to {@code cellEditor.getControl()} work.
 */
public class CellEditorControl extends Control {
  private final CellEditor editor;

  public CellEditorControl(Composite parent, int style, CellEditor editor) {
    super(parent, style, "celleditor");
    this.editor = editor;
    created();
  }

  public CellEditor getCellEditor() {
    return editor;
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if (d.has("value"))
      editor.valueFromClient(d.get("value"));
    super.onClientEvent(type, d);
  }
}
