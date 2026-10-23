package com.unitesk.requality.vscode.port.eclipse.views.compare;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.IViewerCreator;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

public class JSONViewerCreator implements IViewerCreator {

  public JSONViewerCreator() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public Viewer createViewer(Composite parent, CompareConfiguration config) {
    return new TreeNodeViewer(parent, config);
  }
}
