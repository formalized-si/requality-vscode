package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/** {@code org.eclipse.ui.IWorkingSetElementAdapter}: adapts the elements added to a working set (the element that is kept). */
public interface IWorkingSetElementAdapter {
  /** Adapts the elements for the working set; an element that cannot be adapted is dropped. */
  IAdaptable[] adaptElements(IWorkingSet ws, IAdaptable[] elements);

  /** Disposes the adapter. */
  void dispose();
}
