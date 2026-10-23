package com.unitesk.requality.vscode.ui.jface.preference;

/** {@code org.eclipse.jface.preference.IPreferenceStore}. */
public interface IPreferenceStore {
  boolean contains(String name);

  boolean getBoolean(String name);

  int getInt(String name);

  long getLong(String name);

  double getDouble(String name);

  String getString(String name);

  boolean getDefaultBoolean(String name);

  int getDefaultInt(String name);

  String getDefaultString(String name);

  void setDefault(String name, boolean value);

  void setDefault(String name, int value);

  void setDefault(String name, long value);

  void setDefault(String name, String value);

  void setValue(String name, boolean value);

  void setValue(String name, int value);

  void setValue(String name, long value);

  void setValue(String name, double value);

  void setValue(String name, String value);

  void setToDefault(String name);

  boolean isDefault(String name);

  boolean needsSaving();

  void addPropertyChangeListener(IPropertyChangeListener listener);

  void removePropertyChangeListener(IPropertyChangeListener listener);
}
