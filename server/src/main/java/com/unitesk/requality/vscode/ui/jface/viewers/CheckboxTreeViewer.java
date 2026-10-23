package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/** {@code org.eclipse.jface.viewers.CheckboxTreeViewer}. */
public class CheckboxTreeViewer extends TreeViewer implements ICheckable {
  private final List<ICheckStateListener> listeners = new CopyOnWriteArrayList<>();

  public CheckboxTreeViewer(Composite parent) {
    this(parent, SWT.BORDER);
  }

  public CheckboxTreeViewer(Composite parent, int style) {
    super(new Tree(parent, SWT.CHECK | style));
  }

  public CheckboxTreeViewer(Tree tree) {
    super(tree);
  }

  @Override
  protected void handleCheck(Widget item) {
    if (!(item instanceof TreeItem))
      return;
    TreeItem ti = (TreeItem) item;
    CheckStateChangedEvent e = new CheckStateChangedEvent(this, ti.getData(), ti.getChecked());
    for (ICheckStateListener l : listeners)
      l.checkStateChanged(e);
  }

  @Override
  public void addCheckStateListener(ICheckStateListener l) {
    listeners.add(l);
  }

  @Override
  public void removeCheckStateListener(ICheckStateListener l) {
    listeners.remove(l);
  }

  @Override
  public boolean getChecked(Object element) {
    TreeItem i = (TreeItem) findItem(element);
    return i != null && i.getChecked();
  }

  @Override
  public boolean setChecked(Object element, boolean state) {
    TreeItem i = (TreeItem) findItem(element);
    if (i == null)
      return false;
    i.setChecked(state);
    return true;
  }

  public boolean setSubtreeChecked(Object element, boolean state) {
    TreeItem i = (TreeItem) findItem(element);
    if (i == null)
      return false;
    check(i, state);
    return true;
  }

  private void check(TreeItem i, boolean state) {
    i.setChecked(state);
    for (TreeItem c : i.getItems())
      check(c, state);
  }

  public boolean setGrayed(Object element, boolean state) {
    TreeItem i = (TreeItem) findItem(element);
    if (i == null)
      return false;
    i.setGrayed(state);
    return true;
  }

  public boolean setGrayChecked(Object element, boolean state) {
    TreeItem i = (TreeItem) findItem(element);
    if (i == null)
      return false;
    i.setGrayed(state);
    i.setChecked(state);
    return true;
  }

  public Object[] getCheckedElements() {
    List<Object> r = new ArrayList<>();
    collectChecked(getTree().getItems(), r);
    return r.toArray();
  }

  private static void collectChecked(TreeItem[] items, List<Object> out) {
    for (TreeItem i : items) {
      if (i.getChecked())
        out.add(i.getData());
      collectChecked(i.getItems(), out);
    }
  }

  public void setCheckedElements(Object[] elements) {
    List<Object> l = Arrays.asList(elements);
    setChecked(getTree().getItems(), l);
  }

  private static void setChecked(TreeItem[] items, List<Object> l) {
    for (TreeItem i : items) {
      i.setChecked(l.contains(i.getData()));
      setChecked(i.getItems(), l);
    }
  }

  public void setAllChecked(boolean state) {
    for (TreeItem i : getTree().getItems())
      check(i, state);
  }
}
