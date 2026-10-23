package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.viewers.ILabelDecorator}. */
public interface ILabelDecorator extends IBaseLabelProvider {
  Image decorateImage(Image image, Object element);

  String decorateText(String text, Object element);
}
