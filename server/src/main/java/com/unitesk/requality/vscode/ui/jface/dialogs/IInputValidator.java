package com.unitesk.requality.vscode.ui.jface.dialogs;

/** {@code org.eclipse.jface.dialogs.IInputValidator}. */
@FunctionalInterface
public interface IInputValidator {
  String isValid(String newText);
}
