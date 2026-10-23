package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IPropertyListener}. */
@FunctionalInterface
public interface IPropertyListener {
  void propertyChanged(Object source, int propId);
}
