package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.compare.contentmergeviewer.IFlushable}: a viewer whose changes are written to its input. */
public interface IFlushable {
  void flush(IProgressMonitor monitor);
}
