package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.viewers.ListViewer}. */
public class ListViewer extends StructuredViewer {
  private final com.unitesk.requality.vscode.ui.swt.widgets.List list;
  private final List<Object> elements = new ArrayList<>();

  public ListViewer(Composite parent) {
    this(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
  }

  public ListViewer(Composite parent, int style) {
    this(new com.unitesk.requality.vscode.ui.swt.widgets.List(parent, style));
  }

  public ListViewer(com.unitesk.requality.vscode.ui.swt.widgets.List list) {
    this.list = list;
    list.addListener(SWT.Selection, e -> fireSelectionChanged(new SelectionChangedEvent(this, getSelection())));
    list.addListener(SWT.DefaultSelection, e -> fireDoubleClick(new DoubleClickEvent(this, getSelection())));
  }

  public com.unitesk.requality.vscode.ui.swt.widgets.List getList() {
    return list;
  }

  @Override
  public Control getControl() {
    return list;
  }

  @Override
  public void refresh() {
    List<Object> sel = new ArrayList<>(getStructuredSelection().toList());
    elements.clear();
    Object input = getInput();
    if (getContentProvider() != null && input != null)
      for (Object o : getFilteredChildren(input, getRawChildren(input)))
        elements.add(o);
    String[] labels = new String[elements.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] = text(elements.get(i), 0);
    list.setItems(labels);
    List<Integer> idx = new ArrayList<>();
    for (int i = 0; i < elements.size(); i++)
      if (sel.contains(elements.get(i)))
        idx.add(i);
    list.setSelection(idx.stream().mapToInt(Integer::intValue).toArray());
  }

  @Override
  public void refresh(Object element) {
    refresh();
  }

  @Override
  public void update(Object element, String[] properties) {
    for (int i = 0; i < elements.size(); i++)
      if (same(elements.get(i), element))
        list.setItem(i, text(element, 0));
  }

  public void add(Object element) {
    refresh();
  }

  public void add(Object[] es) {
    refresh();
  }

  public void remove(Object element) {
    refresh();
  }

  public void remove(Object[] es) {
    refresh();
  }

  public Object getElementAt(int index) {
    return index >= 0 && index < elements.size() ? elements.get(index) : null;
  }

  @Override
  public ISelection getSelection() {
    List<Object> r = new ArrayList<>();
    for (int i : list.getSelectionIndices())
      if (i < elements.size())
        r.add(elements.get(i));
    return new StructuredSelection(r);
  }

  @Override
  public void setSelection(ISelection selection, boolean reveal) {
    List<Integer> idx = new ArrayList<>();
    if (selection instanceof IStructuredSelection)
      for (Object o : ((IStructuredSelection) selection).toList())
        for (int i = 0; i < elements.size(); i++)
          if (same(elements.get(i), o))
            idx.add(i);
    list.setSelection(idx.stream().mapToInt(Integer::intValue).toArray());
    fireSelectionChanged(new SelectionChangedEvent(this, getSelection()));
  }

  @Override
  public void reveal(Object element) {
  }
}
