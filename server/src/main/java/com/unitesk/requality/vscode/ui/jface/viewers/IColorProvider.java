package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Color;

/** {@code org.eclipse.jface.viewers.IColorProvider}. */
public interface IColorProvider {
  Color getForeground(Object element);

  Color getBackground(Object element);
}
