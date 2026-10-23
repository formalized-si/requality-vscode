package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.Collection;

/** {@code org.eclipse.jface.viewers.ArrayContentProvider}. */
public class ArrayContentProvider implements IStructuredContentProvider {
  private static final ArrayContentProvider INSTANCE = new ArrayContentProvider();

  public static ArrayContentProvider getInstance() {
    return INSTANCE;
  }

  @Override
  public Object[] getElements(Object inputElement) {
    if (inputElement instanceof Object[])
      return (Object[]) inputElement;
    if (inputElement instanceof Collection)
      return ((Collection<?>) inputElement).toArray();
    return new Object[0];
  }
}
