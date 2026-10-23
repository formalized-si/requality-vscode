package com.unitesk.requality.vscode.ui.team.ui.history;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.team.ui.history.IHistoryCompareAdapter}: a history page that turns its selected revisions into compare
 * inputs (for the history compare editors).
 */
public interface IHistoryCompareAdapter {
  /** The compare input of the selected object, or null. */
  ICompareInput getCompareInput(Object object);

  /** Prepares the labels and images of the compare input and its configuration. */
  void prepareInput(ICompareInput input, CompareConfiguration configuration, IProgressMonitor monitor)
      throws InvocationTargetException;
}
