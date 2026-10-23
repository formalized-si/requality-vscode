package com.unitesk.requality.vscode.ui.runtime;

/**
 * {@code org.eclipse.core.runtime.IAdapterFactory}: provides adapters for the objects of the adaptable types it
 * lists in {@link #getAdapterList()}.
 */
public interface IAdapterFactory {

  /**
   * {@code org.eclipse.core.runtime.IAdapterFactory#getAdapter}: the adapter of the given type for the object, or
   * {@code null}.
   */
  @SuppressWarnings("rawtypes")
  Object getAdapter(Object adaptableObject, Class adapterType);

  /** {@code org.eclipse.core.runtime.IAdapterFactory#getAdapterList}: the adapter types this factory provides. */
  @SuppressWarnings("rawtypes")
  Class[] getAdapterList();
}
