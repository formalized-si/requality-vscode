package com.unitesk.requality.vscode.port.eclipse.views.compare;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.IViewerCreator;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

public class JSONCompareViewerCreator implements IViewerCreator {

  public JSONCompareViewerCreator() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public Viewer createViewer(Composite parent, CompareConfiguration config) {
    return new TreeNodeCompareViewer(parent, config);
  }
}
