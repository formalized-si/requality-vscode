package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.dialogs.ListSelectionDialog}: a check list. */
public class ListSelectionDialog extends SelectionDialog {
  private final Object input;
  private final IStructuredContentProvider contentProvider;
  private final ILabelProvider labelProvider;
  private CheckboxTableViewer viewer;

  public ListSelectionDialog(Shell parentShell, Object input, IStructuredContentProvider contentProvider,
      ILabelProvider labelProvider, String message) {
    super(parentShell);
    this.input = input;
    this.contentProvider = contentProvider;
    this.labelProvider = labelProvider;
    setMessage(message);
  }

  public CheckboxTableViewer getViewer() {
    return viewer;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite c = (Composite) super.createDialogArea(parent);
    createMessageArea(c);
    viewer = CheckboxTableViewer.newCheckList(c, SWT.BORDER | SWT.V_SCROLL);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 250;
    gd.widthHint = 350;
    viewer.getTable().setLayoutData(gd);
    viewer.setContentProvider(contentProvider);
    viewer.setLabelProvider(labelProvider);
    viewer.setInput(input);
    if (!getInitialElementSelections().isEmpty())
      viewer.setCheckedElements(getInitialElementSelections().toArray());
    return c;
  }

  @Override
  protected void okPressed() {
    setSelectionResult(viewer.getCheckedElements());
    super.okPressed();
  }
}
