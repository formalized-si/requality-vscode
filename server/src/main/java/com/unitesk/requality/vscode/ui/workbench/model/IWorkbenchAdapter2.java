package com.unitesk.requality.vscode.ui.workbench.model;

import com.unitesk.requality.vscode.ui.swt.graphics.FontData;
import com.unitesk.requality.vscode.ui.swt.graphics.RGB;

/** {@code org.eclipse.ui.model.IWorkbenchAdapter2}: colors and font of a workbench model object (null = default). */
public interface IWorkbenchAdapter2 {
  RGB getForeground(Object element);

  RGB getBackground(Object element);

  FontData getFont(Object element);
}
