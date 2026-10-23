package com.unitesk.requality.vscode.ui.navigator.filters;

/** {@code org.eclipse.ui.navigator.ICommonFilterDescriptor}: a {@code commonFilter} of plugin.xml. */
public interface ICommonFilterDescriptor {
  String getId();

  String getName();

  String getDescription();

  boolean isActiveByDefault();

  boolean isVisibleInUi();
}
