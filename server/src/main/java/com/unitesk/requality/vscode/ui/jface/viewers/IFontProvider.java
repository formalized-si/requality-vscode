package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Font;

/** {@code org.eclipse.jface.viewers.IFontProvider}. */
public interface IFontProvider {
  Font getFont(Object element);
}
