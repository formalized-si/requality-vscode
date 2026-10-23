package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IAggregateWorkingSet}: the union of other working sets. */
public interface IAggregateWorkingSet extends IWorkingSet {
  /** The component working sets. */
  IWorkingSet[] getComponents();
}
