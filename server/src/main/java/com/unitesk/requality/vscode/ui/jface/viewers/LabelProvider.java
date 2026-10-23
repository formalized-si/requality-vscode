package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.viewers.LabelProvider}. */
public class LabelProvider implements ILabelProvider {
  private final List<ILabelProviderListener> listeners = new CopyOnWriteArrayList<>();

  @Override
  public Image getImage(Object element) {
    return null;
  }

  @Override
  public String getText(Object element) {
    return element == null ? "" : element.toString();
  }

  @Override
  public void addListener(ILabelProviderListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(ILabelProviderListener listener) {
    listeners.remove(listener);
  }

  protected void fireLabelProviderChanged(LabelProviderChangedEvent event) {
    for (ILabelProviderListener l : listeners)
      l.labelProviderChanged(event);
  }
}
