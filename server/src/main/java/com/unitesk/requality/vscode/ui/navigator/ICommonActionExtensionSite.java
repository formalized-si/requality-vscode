package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;

/** {@code org.eclipse.ui.navigator.ICommonActionExtensionSite}: what an action provider is initialized with. */
public interface ICommonActionExtensionSite {
  String getExtensionId();

  ICommonViewerSite getViewSite();

  StructuredViewer getStructuredViewer();

  INavigatorContentService getContentService();
}
