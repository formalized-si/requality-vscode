package com.unitesk.requality.vscode.ui.jface.dialogs;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/** {@code org.eclipse.jface.dialogs.IDialogSettings}. */
public interface IDialogSettings {
  IDialogSettings addNewSection(String name);

  void addSection(IDialogSettings section);

  String get(String key);

  String[] getArray(String key);

  boolean getBoolean(String key);

  double getDouble(String key) throws NumberFormatException;

  float getFloat(String key) throws NumberFormatException;

  int getInt(String key) throws NumberFormatException;

  long getLong(String key) throws NumberFormatException;

  String getName();

  IDialogSettings getSection(String sectionName);

  IDialogSettings[] getSections();

  void load(Reader reader) throws IOException;

  void load(String fileName) throws IOException;

  void put(String key, String[] value);

  void put(String key, double value);

  void put(String key, float value);

  void put(String key, int value);

  void put(String key, long value);

  void put(String key, String value);

  void put(String key, boolean value);

  void save(Writer writer) throws IOException;

  void save(String fileName) throws IOException;
}
