package com.unitesk.requality.vscode.ui.jface.text;

/** {@code org.eclipse.jface.text.IDocument}. */
public interface IDocument {
  String get();

  void set(String text);

  int getLength();
}
