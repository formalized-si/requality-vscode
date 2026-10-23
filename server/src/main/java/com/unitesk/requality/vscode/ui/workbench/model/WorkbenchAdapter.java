package com.unitesk.requality.vscode.ui.workbench.model;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;

/** {@code org.eclipse.ui.model.WorkbenchAdapter}: default implementation of {@link IWorkbenchAdapter}. */
public class WorkbenchAdapter implements IWorkbenchAdapter, IWorkbenchAdapter2 {

  public WorkbenchAdapter() {
  }

  /** The children of the object (none by default). */
  @Override
  public Object[] getChildren(Object o) {
    return new Object[0];
  }

  /** The image of the object (none by default). */
  @Override
  public ImageDescriptor getImageDescriptor(Object object) {
    return null;
  }

  /** The label of the object: its string form. */
  @Override
  public String getLabel(Object o) {
    return o.toString();
  }

  /** The parent of the object (none by default). */
  @Override
  public Object getParent(Object o) {
    return null;
  }

  /** {@code WorkbenchAdapter.getForeground}: the default color. */
  @Override
  public com.unitesk.requality.vscode.ui.swt.graphics.RGB getForeground(Object element) {
    return null;
  }

  @Override
  public com.unitesk.requality.vscode.ui.swt.graphics.RGB getBackground(Object element) {
    return null;
  }

  @Override
  public com.unitesk.requality.vscode.ui.swt.graphics.FontData getFont(Object element) {
    return null;
  }
}
