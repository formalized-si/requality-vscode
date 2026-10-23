package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/** {@code org.eclipse.ui.IElementFactory}: re-creates a persisted element from its memento. */
public interface IElementFactory {
  /** Creates the element of the memento, or null when it cannot be created. */
  IAdaptable createElement(IMemento memento);
}
