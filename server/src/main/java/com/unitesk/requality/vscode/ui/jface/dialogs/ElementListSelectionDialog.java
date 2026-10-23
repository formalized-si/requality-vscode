package com.unitesk.requality.vscode.ui.jface.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/** {@code org.eclipse.ui.dialogs.ElementListSelectionDialog}: a filter field above a list of elements. */
public class ElementListSelectionDialog extends SelectionDialog {
  private final ILabelProvider labelProvider;
  private Object[] elements = new Object[0];
  private boolean multiple;
  private String filter = "";
  private final List<Object> shown = new ArrayList<>();
  private com.unitesk.requality.vscode.ui.swt.widgets.List list;
  private boolean ignoreCase = true;

  public ElementListSelectionDialog(Shell parent, ILabelProvider renderer) {
    super(parent);
    this.labelProvider = renderer;
  }

  public void setElements(Object[] elements) {
    this.elements = elements == null ? new Object[0] : elements;
  }

  public void setMultipleSelection(boolean b) {
    multiple = b;
  }

  public void setFilter(String filter) {
    this.filter = filter == null ? "" : filter;
  }

  public void setIgnoreCase(boolean b) {
    ignoreCase = b;
  }

  public void setEmptyListMessage(String m) {
  }

  public void setSize(int w, int h) {
  }

  public Object getFirstResult() {
    Object[] r = getResult();
    return r == null || r.length == 0 ? null : r[0];
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite c = (Composite) super.createDialogArea(parent);
    createMessageArea(c);
    Text text = new Text(c, SWT.BORDER);
    text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    text.setText(filter);
    list = new com.unitesk.requality.vscode.ui.swt.widgets.List(c, (multiple ? SWT.MULTI : SWT.SINGLE) | SWT.BORDER | SWT.V_SCROLL);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 250;
    gd.widthHint = 350;
    list.setLayoutData(gd);
    text.addModifyListener(e -> {
      filter = text.getText();
      fill();
    });
    list.addListener(SWT.Selection, e -> updateOk());
    list.addListener(SWT.DefaultSelection, e -> {
      if (list.getSelectionCount() > 0)
        okPressed();
    });
    fill();
    return c;
  }

  private boolean matches(String label) {
    String f = filter.replace("*", "");
    if (f.isEmpty())
      return true;
    return ignoreCase ? label.toLowerCase().startsWith(f.toLowerCase()) || label.toLowerCase().contains(f.toLowerCase())
        : label.contains(f);
  }

  private void fill() {
    shown.clear();
    List<String> labels = new ArrayList<>();
    Object[] sorted = elements.clone();
    Arrays.sort(sorted, (a, b) -> String.valueOf(labelProvider.getText(a)).compareToIgnoreCase(String.valueOf(labelProvider.getText(b))));
    for (Object o : sorted) {
      String l = labelProvider.getText(o);
      if (l == null)
        l = "";
      if (matches(l)) {
        shown.add(o);
        labels.add(l);
      }
    }
    list.setItems(labels.toArray(new String[0]));
    if (!labels.isEmpty())
      list.select(0);
    updateOk();
  }

  private void updateOk() {
    if (getButton(IDialogConstants.OK_ID) != null)
      getButton(IDialogConstants.OK_ID).setEnabled(list.getSelectionCount() > 0);
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    super.createButtonsForButtonBar(parent);
    updateOk();
  }

  @Override
  protected void okPressed() {
    List<Object> r = new ArrayList<>();
    for (int i : list.getSelectionIndices())
      r.add(shown.get(i));
    setResult(r);
    super.okPressed();
  }
}
