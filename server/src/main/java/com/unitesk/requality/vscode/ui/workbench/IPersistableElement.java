package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IPersistableElement}. */
public interface IPersistableElement {
  /** Returns the id of the factory that recreates this element. */
  String getFactoryId();

  /** Saves the state of this element into the given memento. */
  void saveState(IMemento memento);
}
