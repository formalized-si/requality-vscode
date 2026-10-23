package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/** {@code org.eclipse.ui.IWorkbenchPropertyPage}. */
public interface IWorkbenchPropertyPage {
  IAdaptable getElement();

  void setElement(IAdaptable element);
}
