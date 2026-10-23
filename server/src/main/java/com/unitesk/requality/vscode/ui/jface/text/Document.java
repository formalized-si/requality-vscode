package com.unitesk.requality.vscode.ui.jface.text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** {@code org.eclipse.jface.text.Document}. */
public class Document implements IDocument {
  private String text;
  final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  public Document() {
    this("");
  }

  public Document(String text) {
    this.text = text == null ? "" : text;
  }

  @Override
  public String get() {
    return text;
  }

  @Override
  public void set(String t) {
    text = t == null ? "" : t;
    for (Runnable r : listeners)
      r.run();
  }

  @Override
  public int getLength() {
    return text.length();
  }
}
