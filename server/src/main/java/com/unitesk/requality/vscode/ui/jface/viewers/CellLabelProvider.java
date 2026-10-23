package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.CellLabelProvider}. */
public abstract class CellLabelProvider implements IBaseLabelProvider {
  public abstract void update(ViewerCell cell);

  public String getToolTipText(Object element) {
    return null;
  }
}
