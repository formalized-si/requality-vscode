package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchContentProvider;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.ui.dialogs.ResourceSelectionDialog}: selects workspace resources below a container. The result is
 * an array of the selected resources.
 */
public class ResourceSelectionDialog extends ElementTreeSelectionDialog {

  public ResourceSelectionDialog(Shell parentShell, Object rootElement, String message) {
    super(parentShell, new WorkbenchLabelProvider(), new WorkbenchContentProvider());
    setTitle("Resource Selection");
    setMessage(message);
    setInput(rootElement);
    setAllowMultiple(true);
  }
}
