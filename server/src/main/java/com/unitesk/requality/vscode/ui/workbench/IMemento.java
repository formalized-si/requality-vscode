package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IMemento}: a persistable tree of typed key/value pairs. */
public interface IMemento {
  IMemento createChild(String type);

  IMemento createChild(String type, String id);

  Float getFloat(String key);

  Integer getInteger(String key);

  String getString(String key);

  Boolean getBoolean(String key);

  String getTextData();

  String getType();

  String getID();

  IMemento getChild(String type);

  IMemento[] getChildren(String type);

  IMemento[] getChildren();

  String[] getAttributeKeys();

  void putFloat(String key, float value);

  void putInteger(String key, int value);

  void putString(String key, String value);

  void putBoolean(String key, boolean value);

  void putMemento(IMemento memento);

  void putTextData(String data);
}
