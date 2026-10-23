package com.unitesk.requality.vscode.port.eclipse.views.atypeeditors;

import com.unitesk.requality.vscode.ui.jface.viewers.DialogCellEditor;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.jface.dialogs.ResourceSelectionDialog;

import com.unitesk.requality.vscode.port.eclipse.Activator;

public class ResourceValueEditor extends DialogCellEditor {

  public ResourceValueEditor(Composite table, int style) {
    super(table, style);
  }

  @Override
  protected Object openDialogBox(Control cellEditorWindow) {
    ResourceSelectionDialog dialog =
        new ResourceSelectionDialog(
            cellEditorWindow.getShell(),
            Activator.getDefault()
                .getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage()
                .getInput(),
            "АБЫРЫБЫР!!");
    //dialog.setInitialSelections(selectedResources);
    dialog.open();
    return dialog.getResult();
  }
}
