package com.unitesk.requality.vscode.ui.commands.operations;

/** {@code org.eclipse.core.commands.operations.ObjectUndoContext}. */
public class ObjectUndoContext implements IUndoContext {
  private final Object object;
  private final String label;

  public ObjectUndoContext(Object object) {
    this(object, String.valueOf(object));
  }

  public ObjectUndoContext(Object object, String label) {
    this.object = object;
    this.label = label;
  }

  public Object getObject() {
    return object;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public boolean matches(IUndoContext context) {
    return context == this;
  }
}
