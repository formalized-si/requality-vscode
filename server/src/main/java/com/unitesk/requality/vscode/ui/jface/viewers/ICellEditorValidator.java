package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ICellEditorValidator}. */
@FunctionalInterface
public interface ICellEditorValidator {
  String isValid(Object value);
}
