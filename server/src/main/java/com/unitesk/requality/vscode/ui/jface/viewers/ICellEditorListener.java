package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ICellEditorListener}. */
public interface ICellEditorListener {
  void applyEditorValue();

  void cancelEditor();

  void editorValueChanged(boolean oldValidState, boolean newValidState);
}
