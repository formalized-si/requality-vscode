package com.unitesk.requality.vscode.ui.resources.team;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.core.resources.team.FileModificationValidator}: the validator of the extension point
 * {@code org.eclipse.core.resources.fileModificationValidator} (team core), asked by {@code IWorkspace.validateEdit}
 * whether read-only files may be modified.
 */
public abstract class FileModificationValidator {
  public abstract IStatus validateEdit(IFile[] files, FileModificationValidationContext context);

  public IStatus validateSave(IFile file) {
    return validateEdit(new IFile[] { file }, null);
  }
}
