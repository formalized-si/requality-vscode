package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/** {@code org.eclipse.ui.IEditorInput}. */
public interface IEditorInput extends IAdaptable {
  boolean exists();

  default ImageDescriptor getImageDescriptor() {
    return null;
  }

  String getName();

  default IPersistableElement getPersistable() {
    return null;
  }

  String getToolTipText();
}
