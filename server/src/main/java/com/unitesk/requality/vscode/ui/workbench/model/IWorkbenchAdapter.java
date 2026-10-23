package com.unitesk.requality.vscode.ui.workbench.model;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;

/** {@code org.eclipse.ui.model.IWorkbenchAdapter}. */
public interface IWorkbenchAdapter {
  Object[] getChildren(Object o);

  ImageDescriptor getImageDescriptor(Object object);

  String getLabel(Object o);

  Object getParent(Object o);
}
