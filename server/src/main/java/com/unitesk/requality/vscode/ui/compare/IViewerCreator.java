package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/** {@code org.eclipse.compare.IViewerCreator}. */
public interface IViewerCreator {
  Viewer createViewer(Composite parent, CompareConfiguration config);
}
