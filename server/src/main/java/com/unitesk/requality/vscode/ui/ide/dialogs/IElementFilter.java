package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.util.Collection;

import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.ui.internal.ide.dialogs.IElementFilter}: filters the elements of a file-system tree. */
public interface IElementFilter {
  void filterElements(Collection elements, IProgressMonitor monitor) throws InterruptedException;

  void filterElements(Object[] elements, IProgressMonitor monitor) throws InterruptedException;
}
