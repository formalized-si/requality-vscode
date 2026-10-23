package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.IConfigurationElement}. */
public interface IConfigurationElement {
  Object createExecutableExtension(String propertyName) throws CoreException;

  String getAttribute(String name) throws InvalidRegistryObjectException;

  String[] getAttributeNames();

  IConfigurationElement[] getChildren();

  IConfigurationElement[] getChildren(String name);

  IExtension getDeclaringExtension();

  String getName();

  Object getParent();

  String getValue();

  String getNamespaceIdentifier();

  IContributor getContributor();

  boolean isValid();
}
