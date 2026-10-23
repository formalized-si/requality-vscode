package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ColumnViewerEditorActivationListener}. */
public abstract class ColumnViewerEditorActivationListener {
  public abstract void beforeEditorActivated(ColumnViewerEditorActivationEvent event);

  public abstract void afterEditorActivated(ColumnViewerEditorActivationEvent event);

  public abstract void beforeEditorDeactivated(ColumnViewerEditorDeactivationEvent event);

  public abstract void afterEditorDeactivated(ColumnViewerEditorDeactivationEvent event);
}
