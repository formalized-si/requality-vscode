package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;

/** {@code org.eclipse.ui.navigator.IExtensionStateModel}: properties of a content extension shared by its parts. */
public interface IExtensionStateModel {
  String getId();

  boolean getBooleanProperty(String aPropertyName);

  void setBooleanProperty(String aPropertyName, boolean aPropertyValue);

  void addPropertyChangeListener(IPropertyChangeListener aListener);

  void removePropertyChangeListener(IPropertyChangeListener aListener);
}
