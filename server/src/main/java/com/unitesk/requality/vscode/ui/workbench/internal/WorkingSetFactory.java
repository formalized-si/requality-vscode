package com.unitesk.requality.vscode.ui.workbench.internal;

import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IElementFactory;
import com.unitesk.requality.vscode.ui.workbench.IMemento;

/** {@code org.eclipse.ui.internal.WorkingSetFactory}: re-creates a working set (or an aggregate one) from its memento. */
public class WorkingSetFactory implements IElementFactory {
  @Override
  public IAdaptable createElement(IMemento memento) {
    String name = memento.getString("name");
    String label = memento.getString("label");
    if (name == null)
      return null;
    if ("true".equals(memento.getString(AbstractWorkingSet.TAG_AGGREGATE)))
      return new AggregateWorkingSet(name, label, memento);
    return new WorkingSet(name, label, memento);
  }
}
