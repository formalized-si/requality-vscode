package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.IBaseLabelProvider}. */
public interface IBaseLabelProvider {
  default void addListener(ILabelProviderListener listener) {
  }

  default void dispose() {
  }

  default boolean isLabelProperty(Object element, String property) {
    return true;
  }

  default void removeListener(ILabelProviderListener listener) {
  }
}
