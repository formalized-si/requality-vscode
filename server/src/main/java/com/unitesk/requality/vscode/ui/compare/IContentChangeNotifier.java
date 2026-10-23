package com.unitesk.requality.vscode.ui.compare;

/** {@code org.eclipse.compare.IContentChangeNotifier}. */
public interface IContentChangeNotifier {
  void addContentChangeListener(IContentChangeListener listener);

  void removeContentChangeListener(IContentChangeListener listener);
}
