package com.unitesk.requality.eclipse.views.atypeeditors;

import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.dialogs.ResourceSelectionDialog;

import com.unitesk.requality.eclipse.Activator;

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
