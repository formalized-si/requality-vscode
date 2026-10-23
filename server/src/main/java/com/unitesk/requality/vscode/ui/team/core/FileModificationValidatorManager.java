package com.unitesk.requality.vscode.ui.team.core;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.team.FileModificationValidationContext;
import com.unitesk.requality.vscode.ui.resources.team.FileModificationValidator;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.team.internal.core.FileModificationValidatorManager}: the file modification validator of the
 * workspace: the files are validated by the validator of their repository provider, and the files of no provider by
 * the default validator ({@link DefaultFileModificationValidator}). The product installs no repository provider, so
 * every file goes to the default validator.
 */
public class FileModificationValidatorManager extends FileModificationValidator {
  private FileModificationValidator defaultValidator;

  @Override
  public IStatus validateEdit(IFile[] files, FileModificationValidationContext context) {
    return getDefaultValidator().validateEdit(files, context);
  }

  @Override
  public IStatus validateSave(IFile file) {
    return getDefaultValidator().validateSave(file);
  }

  private synchronized FileModificationValidator getDefaultValidator() {
    if (defaultValidator == null)
      defaultValidator = new DefaultFileModificationValidator();
    return defaultValidator;
  }
}
