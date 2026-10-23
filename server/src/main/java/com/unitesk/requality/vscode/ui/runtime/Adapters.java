package com.unitesk.requality.vscode.ui.runtime;

/**
 * {@code org.eclipse.core.runtime.Adapters}: the object itself when it is of the type, else its
 * {@link IAdaptable#getAdapter(Class)} (through the adapter manager).
 */
public final class Adapters {
  private Adapters() {
  }

  /** {@code Adapters.adapt(adaptable, adapter)}: the adapter of the type, or null. */
  public static <T> T adapt(Object adaptable, Class<T> adapter) {
    if (adaptable == null)
      return null;
    if (adapter.isInstance(adaptable))
      return adapter.cast(adaptable);
    if (adaptable instanceof IAdaptable) {
      T a = ((IAdaptable) adaptable).getAdapter(adapter);
      if (a != null)
        return a;
    }
    Object a = Platform.getAdapterManager().getAdapter(adaptable, adapter);
    return a == null ? null : adapter.cast(a);
  }
}
