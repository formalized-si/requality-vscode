package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.Iterator;
import java.util.List;

/** {@code org.eclipse.jface.viewers.IStructuredSelection}. */
@SuppressWarnings("rawtypes")
public interface IStructuredSelection extends ISelection, Iterable {
  Object getFirstElement();

  Iterator iterator();

  int size();

  Object[] toArray();

  List toList();
}
