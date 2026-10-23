package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.dialogs.SaveAsDialog}: asks where a new file is saved.
 * The Eclipse dialog picks a folder and a name inside the workspace; here the native VS Code save dialog is shown and
 * {@link #getResult()} returns the chosen file as an absolute {@link IPath} (null if cancelled).
 */
public class SaveAsDialog extends Window {
  private String title = "";
  private IPath result;

  public SaveAsDialog(Shell parentShell) {
    super(parentShell);
  }

  public void setTitle(String newTitle) {
    title = newTitle == null ? "" : newTitle;
  }

  public String getTitle() {
    return title;
  }

  /** Shows the save dialog; returns {@link #OK} with the chosen file, or {@link #CANCEL}. */
  @Override
  public int open() {
    FileDialog dialog = new FileDialog(getParentShell(), SWT.SAVE);
    dialog.setText(title);
    String path = dialog.open(); // VSCODE: native VS Code save dialog instead of the workspace SaveAsDialog
    if (path == null) {
      result = null;
      setReturnCode(CANCEL);
      return CANCEL;
    }
    result = Path.fromOSString(path);
    setReturnCode(OK);
    return OK;
  }

  /** The chosen file, or null if the dialog was cancelled. */
  public IPath getResult() {
    return result;
  }
}
