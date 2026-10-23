package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.viewers.ILabelProvider}. */
public interface ILabelProvider extends IBaseLabelProvider {
  Image getImage(Object element);

  String getText(Object element);
}
