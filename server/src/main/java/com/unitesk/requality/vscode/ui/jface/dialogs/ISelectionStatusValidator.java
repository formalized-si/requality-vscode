package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.ui.dialogs.ISelectionStatusValidator}. */
@FunctionalInterface
public interface ISelectionStatusValidator {
  IStatus validate(Object[] selection);
}
