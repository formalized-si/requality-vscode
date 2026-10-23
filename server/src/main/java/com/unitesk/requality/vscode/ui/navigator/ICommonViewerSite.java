package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.navigator.ICommonViewerSite}: the site of the viewer of a navigator. */
public interface ICommonViewerSite {
  String getId();

  Shell getShell();

  ISelectionProvider getSelectionProvider();

  void setSelectionProvider(ISelectionProvider provider);

  default <T> T getAdapter(Class<T> adapter) {
    return null;
  }
}
