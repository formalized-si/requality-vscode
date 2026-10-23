package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.dialogs.IDialogPage}. */
public interface IDialogPage {
  void createControl(Composite parent);

  Control getControl();

  String getTitle();

  void setTitle(String title);

  void setDescription(String description);

  String getDescription();

  String getErrorMessage();

  String getMessage();

  void setVisible(boolean visible);

  void dispose();
}
