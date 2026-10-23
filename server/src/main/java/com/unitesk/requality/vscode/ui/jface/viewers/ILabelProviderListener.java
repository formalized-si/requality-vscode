package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ILabelProviderListener}. */
@FunctionalInterface
public interface ILabelProviderListener {
  void labelProviderChanged(LabelProviderChangedEvent event);
}
