package com.unitesk.requality.vscode.ui.commands.operations;

import java.util.ArrayList;
import java.util.List;

/** {@code org.eclipse.core.commands.operations.AbstractOperation}. */
public abstract class AbstractOperation implements IUndoableOperation {
  private final List<IUndoContext> contexts = new ArrayList<>();
  private String label;

  public AbstractOperation(String label) {
    this.label = label == null ? "" : label;
  }

  @Override
  public void addContext(IUndoContext context) {
    if (!contexts.contains(context))
      contexts.add(context);
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public boolean canRedo() {
    return true;
  }

  @Override
  public boolean canUndo() {
    return true;
  }

  @Override
  public void dispose() {
  }

  @Override
  public IUndoContext[] getContexts() {
    return contexts.toArray(new IUndoContext[0]);
  }

  @Override
  public String getLabel() {
    return label;
  }

  public void setLabel(String name) {
    label = name;
  }

  @Override
  public boolean hasContext(IUndoContext context) {
    for (IUndoContext c : contexts)
      if (c == context || c.matches(context))
        return true;
    return false;
  }

  @Override
  public void removeContext(IUndoContext context) {
    contexts.remove(context);
  }

  @Override
  public String toString() {
    return label;
  }
}
