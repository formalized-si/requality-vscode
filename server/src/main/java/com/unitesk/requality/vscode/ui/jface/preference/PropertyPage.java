package com.unitesk.requality.vscode.ui.jface.preference;

import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPropertyPage;

/** {@code org.eclipse.ui.dialogs.PropertyPage}: the element is the selected project (an IProject). */
public abstract class PropertyPage extends PreferencePage implements IWorkbenchPropertyPage {
  private IAdaptable element;

  @Override
  public IAdaptable getElement() {
    return element;
  }

  @Override
  public void setElement(IAdaptable element) {
    this.element = element;
  }
}
