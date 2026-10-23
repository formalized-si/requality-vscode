package com.unitesk.requality.eclipse.views.compare;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.IViewerCreator;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Composite;

public class JSONViewerCreator implements IViewerCreator {

  public JSONViewerCreator() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public Viewer createViewer(Composite parent, CompareConfiguration config) {
    return new TreeNodeViewer(parent, config);
  }
}
