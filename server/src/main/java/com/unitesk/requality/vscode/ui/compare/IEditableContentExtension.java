package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.compare.IEditableContentExtension}: the read-only state of an editable element. */
public interface IEditableContentExtension {
  boolean isReadOnly();

  IStatus validateEdit(Shell shell);
}
