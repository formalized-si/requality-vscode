package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.model.PerspectiveLabelProvider;

/**
 * {@code org.eclipse.ui.internal.dialogs.SavePerspectiveDialog} (Window &gt; Perspective &gt; Save Perspective As...): the
 * name of the perspective to save the current one as, and the list of the existing perspectives. Selecting a perspective
 * copies its name; a name that exists asks before the perspective is overwritten.
 */
public class SavePerspectiveDialog extends Dialog implements ISelectionChangedListener, ModifyListener {
  private static final int LIST_WIDTH = 40;
  private static final int TEXT_WIDTH = 40;
  private static final int LIST_HEIGHT = 14;

  private Text text;
  private TableViewer list;
  private Button okButton;
  private final PerspectiveRegistry perspReg;
  private String perspName;
  private IPerspectiveDescriptor persp;
  private IPerspectiveDescriptor initialSelection;
  private boolean ignoreSelection = false;

  public SavePerspectiveDialog(Shell parentShell, PerspectiveRegistry perspReg) {
    super(parentShell);
    this.perspReg = perspReg;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Save Perspective As...");
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    okButton = createButton(parent, IDialogConstants.OK_ID, "&Save", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    updateButtons();
    text.setFocus();
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);

    Label descLabel = new Label(composite, SWT.WRAP);
    descLabel.setText("Enter or select a name to save the current\nperspective as.");

    Label label = new Label(composite, SWT.NONE);
    GridData data = new GridData();
    data.heightHint = 8;
    label.setLayoutData(data);

    Composite nameGroup = new Composite(composite, SWT.NONE);
    nameGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginWidth = layout.marginHeight = 0;
    nameGroup.setLayout(layout);

    label = new Label(nameGroup, SWT.NONE);
    label.setText("&Name:");

    text = new Text(nameGroup, SWT.BORDER);
    text.setFocus();
    data = new GridData(GridData.FILL_HORIZONTAL);
    data.widthHint = convertWidthInCharsToPixels(TEXT_WIDTH);
    text.setLayoutData(data);
    text.addModifyListener(this);

    label = new Label(composite, SWT.NONE);
    data = new GridData();
    data.heightHint = 5;
    label.setLayoutData(data);

    label = new Label(composite, SWT.NONE);
    label.setText("&Existing Perspectives:");

    list = new TableViewer(composite, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
    list.setLabelProvider(new PerspectiveLabelProvider());
    list.setContentProvider(new ArrayContentProvider());
    list.setComparator(new ViewerComparator());
    list.setInput(perspReg.getPerspectives());
    list.addSelectionChangedListener(this);
    Control ctrl = list.getControl();
    GridData spec = new GridData(GridData.FILL_BOTH);
    spec.widthHint = convertWidthInCharsToPixels(LIST_WIDTH);
    spec.heightHint = convertHeightInCharsToPixels(LIST_HEIGHT);
    ctrl.setLayoutData(spec);

    if (initialSelection != null)
      list.setSelection(new com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection(initialSelection), true);

    text.selectAll();
    return composite;
  }

  /** The perspective chosen from the list, or the existing one with the typed name; null for a new name. */
  public IPerspectiveDescriptor getPersp() {
    return persp;
  }

  /** The name typed or chosen. */
  public String getPerspName() {
    return perspName;
  }

  /** The user has typed some text: the list shows the perspective with the name, if there is one. */
  @Override
  public void modifyText(ModifyEvent e) {
    perspName = text.getText();
    ignoreSelection = true;
    persp = perspReg.findPerspectiveWithLabel(perspName);
    if (persp == null)
      list.setSelection(new com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection());
    else
      list.setSelection(new com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection(persp));
    ignoreSelection = false;
    updateButtons();
  }

  /** The name of an existing perspective is confirmed before it is overwritten. */
  @Override
  protected void okPressed() {
    perspName = text.getText();
    persp = perspReg.findPerspectiveWithLabel(perspName);
    if (persp != null) {
      String message = "A perspective with the name '" + perspName + "' already exists. Do you want to overwrite?";
      String[] buttons = new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL,
          IDialogConstants.CANCEL_LABEL };
      MessageDialog d = new MessageDialog(getShell(), "Overwrite Perspective", null, message, MessageDialog.NONE, 0,
          buttons);
      switch (d.open()) {
        case 0:
          break;
        case 1:
          return;
        case 2:
          cancelPressed();
          return;
        default:
          return;
      }
    }
    super.okPressed();
  }

  @Override
  public void selectionChanged(SelectionChangedEvent event) {
    if (ignoreSelection)
      return;
    IStructuredSelection sel = event.getStructuredSelection();
    persp = null;
    if (!sel.isEmpty())
      persp = (IPerspectiveDescriptor) sel.getFirstElement();
    if (persp != null) {
      perspName = persp.getLabel();
      text.setText(perspName);
    }
    updateButtons();
  }

  /** Sets the perspective selected in the list when the dialog opens: the current perspective of the page. */
  public void setInitialSelection(IPerspectiveDescriptor selectedElement) {
    initialSelection = selectedElement;
  }

  private void updateButtons() {
    if (okButton != null)
      okButton.setEnabled(perspReg.validateLabel(text.getText()));
  }
}
