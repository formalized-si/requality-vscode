package com.unitesk.requality.vscode.ui.navigator;

/** {@code org.eclipse.ui.navigator.INavigatorContentDescriptor}: a {@code navigatorContent} extension. */
public interface INavigatorContentDescriptor {
  String getId();

  String getName();

  boolean isActiveByDefault();
}
