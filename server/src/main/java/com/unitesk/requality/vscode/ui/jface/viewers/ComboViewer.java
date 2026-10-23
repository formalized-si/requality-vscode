package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.viewers.ComboViewer}. */
public class ComboViewer extends StructuredViewer {
  private final Combo combo;
  private final List<Object> elements = new ArrayList<>();

  public ComboViewer(Composite parent) {
    this(parent, SWT.READ_ONLY | SWT.BORDER);
  }

  public ComboViewer(Composite parent, int style) {
    this(new Combo(parent, style));
  }

  public ComboViewer(Combo combo) {
    this.combo = combo;
    combo.addListener(SWT.Selection, e -> fireSelectionChanged(new SelectionChangedEvent(this, getSelection())));
  }

  public Combo getCombo() {
    return combo;
  }

  @Override
  public Control getControl() {
    return combo;
  }

  @Override
  public void refresh() {
    Object sel = getStructuredSelection().getFirstElement();
    elements.clear();
    Object input = getInput();
    if (getContentProvider() != null && input != null)
      for (Object o : getFilteredChildren(input, getRawChildren(input)))
        elements.add(o);
    String[] labels = new String[elements.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] = text(elements.get(i), 0);
    combo.setItems(labels);
    for (int i = 0; i < elements.size(); i++)
      if (same(elements.get(i), sel))
        combo.select(i);
  }

  @Override
  public void refresh(Object element) {
    refresh();
  }

  @Override
  public void update(Object element, String[] properties) {
    for (int i = 0; i < elements.size(); i++)
      if (same(elements.get(i), element))
        combo.setItem(i, text(element, 0));
  }

  public void add(Object element) {
    refresh();
  }

  public void remove(Object element) {
    refresh();
  }

  public Object getElementAt(int index) {
    return index >= 0 && index < elements.size() ? elements.get(index) : null;
  }

  @Override
  public ISelection getSelection() {
    int i = combo.getSelectionIndex();
    return i < 0 || i >= elements.size() ? StructuredSelection.EMPTY : new StructuredSelection(elements.get(i));
  }

  @Override
  public void setSelection(ISelection selection, boolean reveal) {
    Object o = selection instanceof IStructuredSelection ? ((IStructuredSelection) selection).getFirstElement() : null;
    boolean found = false;
    for (int i = 0; i < elements.size(); i++)
      if (same(elements.get(i), o)) {
        combo.select(i);
        found = true;
        break;
      }
    if (!found)
      combo.deselectAll();
    fireSelectionChanged(new SelectionChangedEvent(this, getSelection()));
  }

  @Override
  public void reveal(Object element) {
  }
}
