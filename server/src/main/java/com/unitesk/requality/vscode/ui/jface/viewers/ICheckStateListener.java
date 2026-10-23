package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ICheckStateListener}. */
@FunctionalInterface
public interface ICheckStateListener {
  void checkStateChanged(CheckStateChangedEvent event);
}
