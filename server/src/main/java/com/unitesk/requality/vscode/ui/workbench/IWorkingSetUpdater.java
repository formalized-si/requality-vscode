package com.unitesk.requality.vscode.ui.workbench;

/**
 * {@code org.eclipse.ui.IWorkingSetUpdater}: updates the content of a working set when the workbench changes (the updater
 * of a working set type, declared by its {@code updaterClass}).
 */
public interface IWorkingSetUpdater {
  /** Adds a working set to this updater. */
  void add(IWorkingSet workingSet);

  /** Removes a working set from this updater; true if the updater changed. */
  boolean remove(IWorkingSet workingSet);

  /** Whether the updater manages the working set. */
  boolean contains(IWorkingSet workingSet);

  /** Disposes the updater (removes its listeners). */
  void dispose();
}
