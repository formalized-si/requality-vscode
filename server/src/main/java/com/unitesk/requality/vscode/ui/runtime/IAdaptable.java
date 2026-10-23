package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.IAdaptable}. */
public interface IAdaptable {
  default <T> T getAdapter(Class<T> adapter) {
    return adapter.isInstance(this) ? adapter.cast(this) : null;
  }
}
