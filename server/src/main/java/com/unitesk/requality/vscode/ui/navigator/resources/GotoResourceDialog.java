package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.dialogs.FilteredResourcesSelectionDialog;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.GotoResourceDialog}: the "Go To Resource" dialog, the
 * resources of the workspace filtered by a pattern.
 */
public class GotoResourceDialog extends FilteredResourcesSelectionDialog {

  /** Creates a new instance of the class. */
  public GotoResourceDialog(Shell parentShell, IContainer container, int typesMask) {
    super(parentShell, false, container, typesMask);
    setTitle("Go To Resource");
  }
}
