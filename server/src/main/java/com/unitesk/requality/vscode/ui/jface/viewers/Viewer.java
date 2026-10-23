package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** {@code org.eclipse.jface.viewers.Viewer}. */
public abstract class Viewer implements ISelectionProvider {
  private final List<ISelectionChangedListener> selectionListeners = new CopyOnWriteArrayList<>();
  private Object data;

  public abstract Control getControl();

  public abstract Object getInput();

  public abstract void setInput(Object input);

  public abstract void refresh();

  @Override
  public abstract ISelection getSelection();

  public abstract void setSelection(ISelection selection, boolean reveal);

  @Override
  public void setSelection(ISelection selection) {
    setSelection(selection, false);
  }

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener l) {
    selectionListeners.add(l);
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener l) {
    selectionListeners.remove(l);
  }

  protected void fireSelectionChanged(SelectionChangedEvent event) {
    for (ISelectionChangedListener l : selectionListeners)
      l.selectionChanged(event);
  }

  public Object getData(String key) {
    return data;
  }

  public void setData(String key, Object value) {
    data = value;
  }
}
