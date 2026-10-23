package com.unitesk.requality.vscode.ui.swt.graphics;

/** {@code org.eclipse.swt.graphics.Resource}: nothing to free in the remote implementation. */
public abstract class Resource {
  private boolean disposed;

  /** The display (Eclipse Device) of the resource. */
  public com.unitesk.requality.vscode.ui.swt.widgets.Display getDevice() {
    return com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault();
  }

  public void dispose() {
    disposed = true;
  }

  public boolean isDisposed() {
    return disposed;
  }
}
