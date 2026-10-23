package com.unitesk.requality.vscode.ui.team.core;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.team.FileModificationValidationContext;
import com.unitesk.requality.vscode.ui.resources.team.FileModificationValidator;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.team.internal.core.DefaultFileModificationValidator}: the validator of the files of no repository
 * provider: the validator plugged in by the Team UI ({@code org.eclipse.team.core.defaultFileModificationValidator})
 * prompts the user to make read-only files writable; without it, edit and save fail on read-only files.
 */
public class DefaultFileModificationValidator extends FileModificationValidator {
  private static final String TEAM_CORE = "org.eclipse.team.core";
  /** {@code ITeamStatus.READ_ONLY_LOCAL}. */
  private static final int READ_ONLY_LOCAL = 279;

  /* A validator plugged in the the Team UI that will prompt the user to make read-only files writable. */
  private FileModificationValidator uiValidator;

  protected IStatus getDefaultStatus(IFile file) {
    return file.isReadOnly()
        ? new Status(IStatus.ERROR, TEAM_CORE, READ_ONLY_LOCAL,
            MessageFormat.format("File {0} is read-only.", file.getFullPath().toString()), null)
        : Status.OK_STATUS;
  }

  @Override
  public IStatus validateEdit(IFile[] files, FileModificationValidationContext context) {
    IFile[] readOnlyFiles = getReadOnly(files);
    if (readOnlyFiles.length == 0)
      return Status.OK_STATUS;
    synchronized (this) {
      if (uiValidator == null)
        uiValidator = loadUIValidator();
    }
    if (uiValidator != null)
      return uiValidator.validateEdit(files, context);
    // There was no plugged in validator so fail gracefully
    return getStatus(files);
  }

  protected IStatus getStatus(IFile[] files) {
    if (files.length == 1)
      return getDefaultStatus(files[0]);
    IStatus[] stati = new Status[files.length];
    boolean allOK = true;
    for (int i = 0; i < files.length; i++) {
      stati[i] = getDefaultStatus(files[i]);
      if (!stati[i].isOK())
        allOK = false;
    }
    return new MultiStatus(TEAM_CORE, 0, stati, allOK ? "OK" : "Some files are read-only.", null);
  }

  private IFile[] getReadOnly(IFile[] files) {
    List<IFile> result = new ArrayList<>(files.length);
    for (IFile file : files)
      if (file.isReadOnly())
        result.add(file);
    return result.toArray(new IFile[result.size()]);
  }

  @Override
  public IStatus validateSave(IFile file) {
    if (!file.isReadOnly())
      return Status.OK_STATUS;
    synchronized (this) {
      if (uiValidator == null)
        uiValidator = loadUIValidator();
    }
    if (uiValidator != null)
      return uiValidator.validateSave(file);
    return getDefaultStatus(file);
  }

  private FileModificationValidator loadUIValidator() {
    for (IConfigurationElement element : ExtensionRegistry.get().getConfigurationElementsFor(TEAM_CORE,
        "defaultFileModificationValidator")) {
      if (!"validator".equals(element.getName()))
        continue;
      try {
        Object o = element.createExecutableExtension("class");
        if (o instanceof FileModificationValidator validator)
          return validator;
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return null;
  }
}
