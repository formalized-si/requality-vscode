package com.unitesk.requality.vscode.ui.resources;

/** {@code org.eclipse.core.resources.IResourceChangeListener}. */
@FunctionalInterface
public interface IResourceChangeListener {
  void resourceChanged(IResourceChangeEvent event);
}
