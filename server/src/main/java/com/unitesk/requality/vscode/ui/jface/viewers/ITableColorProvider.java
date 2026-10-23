package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Color;

/** {@code org.eclipse.jface.viewers.ITableColorProvider}. */
public interface ITableColorProvider {
  Color getForeground(Object element, int columnIndex);

  Color getBackground(Object element, int columnIndex);
}
