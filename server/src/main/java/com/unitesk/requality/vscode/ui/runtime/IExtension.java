package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.IExtension}. */
public interface IExtension {
  IConfigurationElement[] getConfigurationElements();

  String getExtensionPointUniqueIdentifier();

  String getLabel();

  String getUniqueIdentifier();

  String getSimpleIdentifier();

  String getNamespaceIdentifier();

  IContributor getContributor();

  boolean isValid();
}
