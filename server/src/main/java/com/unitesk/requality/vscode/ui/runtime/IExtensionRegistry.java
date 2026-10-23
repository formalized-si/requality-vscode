package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.IExtensionRegistry}. */
public interface IExtensionRegistry {
  IConfigurationElement[] getConfigurationElementsFor(String extensionPointId);

  IConfigurationElement[] getConfigurationElementsFor(String namespace, String extensionPointName);

  IExtensionPoint getExtensionPoint(String extensionPointId);

  IExtension[] getExtensions(String namespace);
}
