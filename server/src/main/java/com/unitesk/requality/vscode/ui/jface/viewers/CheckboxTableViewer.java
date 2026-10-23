package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/** {@code org.eclipse.jface.viewers.CheckboxTableViewer}. */
public class CheckboxTableViewer extends TableViewer implements ICheckable {
  private final List<ICheckStateListener> listeners = new CopyOnWriteArrayList<>();
  private ICheckStateProvider checkStateProvider;

  public CheckboxTableViewer(Table table) {
    super(table);
  }

  public static CheckboxTableViewer newCheckList(Composite parent, int style) {
    return new CheckboxTableViewer(new Table(parent, SWT.CHECK | style));
  }

  public void setCheckStateProvider(ICheckStateProvider p) {
    checkStateProvider = p;
    refresh();
  }

  @Override
  protected void afterRefresh() {
    if (checkStateProvider == null)
      return;
    for (TableItem i : getTable().getItems()) {
      i.setChecked(checkStateProvider.isChecked(i.getData()));
      i.setGrayed(checkStateProvider.isGrayed(i.getData()));
    }
  }

  @Override
  protected void handleCheck(Widget item) {
    if (!(item instanceof TableItem))
      return;
    TableItem ti = (TableItem) item;
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
    TableItem i = (TableItem) findItem(element);
    return i != null && i.getChecked();
  }

  @Override
  public boolean setChecked(Object element, boolean state) {
    TableItem i = (TableItem) findItem(element);
    if (i == null)
      return false;
    i.setChecked(state);
    return true;
  }

  public boolean getGrayed(Object element) {
    TableItem i = (TableItem) findItem(element);
    return i != null && i.getGrayed();
  }

  public boolean setGrayed(Object element, boolean state) {
    TableItem i = (TableItem) findItem(element);
    if (i == null)
      return false;
    i.setGrayed(state);
    return true;
  }

  public Object[] getCheckedElements() {
    List<Object> r = new ArrayList<>();
    for (TableItem i : getTable().getItems())
      if (i.getChecked())
        r.add(i.getData());
    return r.toArray();
  }

  public void setCheckedElements(Object[] elements) {
    List<Object> l = Arrays.asList(elements);
    for (TableItem i : getTable().getItems())
      i.setChecked(l.contains(i.getData()));
  }

  public Object[] getGrayedElements() {
    List<Object> r = new ArrayList<>();
    for (TableItem i : getTable().getItems())
      if (i.getGrayed())
        r.add(i.getData());
    return r.toArray();
  }

  public void setGrayedElements(Object[] elements) {
    List<Object> l = Arrays.asList(elements);
    for (TableItem i : getTable().getItems())
      i.setGrayed(l.contains(i.getData()));
  }

  public void setAllChecked(boolean state) {
    for (TableItem i : getTable().getItems())
      i.setChecked(state);
  }

  public void setAllGrayed(boolean state) {
    for (TableItem i : getTable().getItems())
      i.setGrayed(state);
  }
}
