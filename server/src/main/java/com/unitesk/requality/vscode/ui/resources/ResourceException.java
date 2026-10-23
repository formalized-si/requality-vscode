package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.core.internal.resources.ResourceException}: a resource operation failed (a {@link CoreException}). */
public class ResourceException extends CoreException {
  private static final long serialVersionUID = 1L;

  public ResourceException(IStatus status) {
    super(status);
  }
}
