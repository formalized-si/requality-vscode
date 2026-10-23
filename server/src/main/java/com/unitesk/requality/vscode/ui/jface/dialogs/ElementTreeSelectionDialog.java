package com.unitesk.requality.vscode.ui.jface.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.dialogs.ElementTreeSelectionDialog}. */
public class ElementTreeSelectionDialog extends SelectionDialog {
  private final ILabelProvider labelProvider;
  private final ITreeContentProvider contentProvider;
  private Object input;
  private TreeViewer viewer;
  private ISelectionStatusValidator validator;
  private boolean allowMultiple = true;
  private ViewerComparator comparator;
  private final List<ViewerFilter> filters = new ArrayList<>();
  private String emptyMessage = "";
  private Label statusLabel;
  private int expandLevel;

  public ElementTreeSelectionDialog(Shell parent, ILabelProvider labelProvider, ITreeContentProvider contentProvider) {
    super(parent);
    this.labelProvider = labelProvider;
    this.contentProvider = contentProvider;
  }

  public void setInput(Object input) {
    this.input = input;
  }

  /** {@code ElementTreeSelectionDialog.setInitialSelection(Object)}: the element selected when the dialog opens. */
  public void setInitialSelection(Object selection) {
    setInitialSelections(selection);
  }

  public void setValidator(ISelectionStatusValidator v) {
    validator = v;
  }

  public void setAllowMultiple(boolean b) {
    allowMultiple = b;
  }

  public void setComparator(ViewerComparator c) {
    comparator = c;
  }

  public void addFilter(ViewerFilter f) {
    filters.add(f);
  }

  public void setEmptyListMessage(String m) {
    emptyMessage = m;
  }

  public void setExpandedElements(Object[] elements) {
  }

  public void setAutoExpandLevel(int level) {
    expandLevel = level;
  }

  public void setSize(int w, int h) {
  }

  public void setDoubleClickSelects(boolean b) {
  }

  public void setHelpAvailable(boolean b) {
  }

  public Object getFirstResult() {
    Object[] r = getResult();
    return r == null || r.length == 0 ? null : r[0];
  }

  public TreeViewer getTreeViewer() {
    return viewer;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite c = (Composite) super.createDialogArea(parent);
    createMessageArea(c);
    viewer = new TreeViewer(c, (allowMultiple ? SWT.MULTI : SWT.SINGLE) | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 300;
    gd.widthHint = 400;
    viewer.getTree().setLayoutData(gd);
    viewer.setContentProvider(contentProvider);
    viewer.setLabelProvider(labelProvider);
    if (comparator != null)
      viewer.setComparator(comparator);
    for (ViewerFilter f : filters)
      viewer.addFilter(f);
    if (expandLevel != 0)
      viewer.setAutoExpandLevel(expandLevel);
    viewer.setInput(input);
    statusLabel = new Label(c, SWT.NONE);
    statusLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    if (viewer.getTree().getItemCount() == 0)
      statusLabel.setText(emptyMessage);
    viewer.addSelectionChangedListener(e -> validate());
    viewer.addDoubleClickListener(e -> {
      if (validate() && getButton(IDialogConstants.OK_ID) != null)
        okPressed();
    });
    if (!getInitialElementSelections().isEmpty())
      viewer.setSelection(new StructuredSelection(getInitialElementSelections()), true);
    return c;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    super.createButtonsForButtonBar(parent);
    validate();
  }

  private boolean validate() {
    Object[] sel = viewer == null ? new Object[0] : viewer.getStructuredSelection().toArray();
    boolean ok;
    String msg = "";
    if (validator != null) {
      IStatus st = validator.validate(sel);
      ok = st == null || st.getSeverity() != IStatus.ERROR;
      msg = st == null || st.isOK() ? "" : st.getMessage();
    } else {
      ok = sel.length > 0;
    }
    if (statusLabel != null && !statusLabel.isDisposed() && viewer.getTree().getItemCount() > 0)
      statusLabel.setText(msg);
    if (getButton(IDialogConstants.OK_ID) != null)
      getButton(IDialogConstants.OK_ID).setEnabled(ok);
    return ok;
  }

  @Override
  protected void okPressed() {
    setSelectionResult(viewer.getStructuredSelection().toArray());
    super.okPressed();
  }
}
