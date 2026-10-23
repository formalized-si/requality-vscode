package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.model.PerspectiveLabelProvider;

/**
 * {@code org.eclipse.ui.internal.dialogs.SelectPerspectiveDialog} (Window &gt; Open Perspective): the perspectives of the
 * registry sorted by name, the default one marked "(default)". The description of the selected perspective is shown
 * below the list (Eclipse shows it in a pop-up on F2). Open, or a double click, selects the perspective.
 */
public class SelectPerspectiveDialog extends Dialog implements ISelectionChangedListener {
  private static final int LIST_HEIGHT = 300;
  private static final int LIST_WIDTH = 300;

  private final IPerspectiveRegistry perspReg;
  private TableViewer list;
  private Label descriptionHint;
  private Button okButton;
  private IPerspectiveDescriptor perspDesc;

  public SelectPerspectiveDialog(Shell parentShell, IPerspectiveRegistry perspReg) {
    super(parentShell);
    this.perspReg = perspReg;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Open Perspective");
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    okButton = createButton(parent, IDialogConstants.OK_ID, "Open", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    updateButtons();
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    list = new TableViewer(composite, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
    list.setLabelProvider(new PerspectiveLabelProvider());
    list.setContentProvider(new ArrayContentProvider());
    list.setComparator(new ViewerComparator());
    list.setInput(perspReg.getPerspectives());
    list.addSelectionChangedListener(this);
    list.addDoubleClickListener(event -> okPressed());
    GridData spec = new GridData(GridData.FILL_BOTH);
    spec.widthHint = LIST_WIDTH;
    spec.heightHint = LIST_HEIGHT;
    list.getControl().setLayoutData(spec);
    descriptionHint = new Label(composite, SWT.WRAP);
    descriptionHint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return composite;
  }

  /** The perspective chosen, or null. */
  public IPerspectiveDescriptor getSelection() {
    return perspDesc;
  }

  @Override
  public void selectionChanged(SelectionChangedEvent event) {
    perspDesc = null;
    IStructuredSelection sel = event.getStructuredSelection();
    if (!sel.isEmpty() && sel.getFirstElement() instanceof IPerspectiveDescriptor)
      perspDesc = (IPerspectiveDescriptor) sel.getFirstElement();
    updateButtons();
    String description = perspDesc == null ? "" : perspDesc.getDescription();
    descriptionHint.setText(description == null ? "" : description);
  }

  private void updateButtons() {
    okButton.setEnabled(perspDesc != null);
  }

  @Override
  protected void cancelPressed() {
    perspDesc = null;
    super.cancelPressed();
  }

  @Override
  protected void okPressed() {
    if (perspDesc == null)
      return;
    super.okPressed();
  }
}
