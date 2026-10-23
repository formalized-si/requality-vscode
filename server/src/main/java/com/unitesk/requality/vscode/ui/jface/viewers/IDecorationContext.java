package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.IDecorationContext}. */
public interface IDecorationContext {
  Object getProperty(String property);

  String[] getProperties();
}
