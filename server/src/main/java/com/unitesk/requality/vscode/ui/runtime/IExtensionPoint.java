package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.IExtensionPoint}. */
public interface IExtensionPoint {
  IConfigurationElement[] getConfigurationElements();

  IExtension[] getExtensions();

  String getUniqueIdentifier();
}
