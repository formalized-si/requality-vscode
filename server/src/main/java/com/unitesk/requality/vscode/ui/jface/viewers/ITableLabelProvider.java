package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.viewers.ITableLabelProvider}. */
public interface ITableLabelProvider extends IBaseLabelProvider {
  Image getColumnImage(Object element, int columnIndex);

  String getColumnText(Object element, int columnIndex);
}
