package com.unitesk.requality.vscode.ui.compare;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.compare.internal.Utilities}: the validation of the resources a compare operation writes (the
 * read-only files are asked to be made editable, {@code IWorkspace.validateEdit}).
 */
public final class Utilities {
  private static final int VALIDATE_EDIT_PROBLEM = 10004;
  private static final String UNABLE_TO_PERFORM = "Operation cannot be performed.";

  private Utilities() {
  }

  public static boolean validateResource(IResource resource, Shell shell, String title) {
    return validateResources(new IResource[] { resource }, shell, title);
  }

  public static boolean validateResources(List<IResource> resources, Shell shell, String title) {
    IResource[] r = resources.toArray(new IResource[resources.size()]);
    return validateResources(r, shell, title);
  }

  /**
   * Whether the resources may be modified: the read-only files are validated for editing; an error is shown when one
   * stays read-only or was modified by the validation.
   */
  public static boolean validateResources(IResource[] resources, Shell shell, String title) {
    // get all readonly files
    List<IResource> readOnlyFiles = getReadonlyFiles(resources);
    if (readOnlyFiles.isEmpty())
      return true;

    // get timestamps of readonly files before validateEdit
    Map<IFile, Long> oldTimeStamps = createModificationStampMap(readOnlyFiles);

    IFile[] files = readOnlyFiles.toArray(new IFile[readOnlyFiles.size()]);
    IStatus status = ResourcesPlugin.getWorkspace().validateEdit(files, shell);
    if (!status.isOK()) {
      displayError(shell, title, status, UNABLE_TO_PERFORM);
      return false;
    }

    IStatus modified = null;
    Map<IFile, Long> newTimeStamps = createModificationStampMap(readOnlyFiles);
    for (Map.Entry<IFile, Long> entry : newTimeStamps.entrySet()) {
      IFile file = entry.getKey();
      Long newTimeStamp = entry.getValue();
      if (file.isReadOnly()) {
        IStatus error = new Status(IStatus.ERROR, CompareUI.PLUGIN_ID, VALIDATE_EDIT_PROBLEM,
            MessageFormat.format("File ''{0}'' is read-only.", file.getFullPath().toString()), null);
        modified = addStatus(modified, error);
      } else if (!oldTimeStamps.get(file).equals(newTimeStamp)) {
        IStatus error = new Status(IStatus.ERROR, CompareUI.PLUGIN_ID, VALIDATE_EDIT_PROBLEM,
            MessageFormat.format("File ''{0}'' has been modified since the beginning of the operation.",
                file.getFullPath().toString()), null);
        modified = addStatus(modified, error);
      }
    }
    if (modified != null) {
      displayError(shell, title, modified, UNABLE_TO_PERFORM);
      return false;
    }
    return true;
  }

  private static void displayError(final Shell shell, final String title, final IStatus status, final String message) {
    if (Display.getCurrent() != null)
      ErrorDialog.openError(shell, title, message, status);
    else
      Display.getDefault().syncExec(() -> ErrorDialog.openError(shell, title, message, status));
  }

  private static List<IResource> getReadonlyFiles(IResource[] resources) {
    List<IResource> readOnlyFiles = new ArrayList<>();
    for (IResource resource : resources) {
      ResourceAttributes resourceAttributes = resource.getResourceAttributes();
      if (resource.getType() == IResource.FILE && resourceAttributes != null && resourceAttributes.isReadOnly())
        readOnlyFiles.add(resource);
    }
    return readOnlyFiles;
  }

  /** VSCODE: the modification stamp of a file is its local time stamp (the file system is the model). */
  private static Map<IFile, Long> createModificationStampMap(List<IResource> files) {
    Map<IFile, Long> map = new HashMap<>();
    for (IResource file : files)
      map.put((IFile) file, file.getLocalTimeStamp());
    return map;
  }

  private static IStatus addStatus(IStatus status, IStatus entry) {
    if (status == null)
      return entry;
    if (status.isMultiStatus()) {
      ((MultiStatus) status).add(entry);
      return status;
    }
    MultiStatus result = new MultiStatus(CompareUI.PLUGIN_ID, VALIDATE_EDIT_PROBLEM, UNABLE_TO_PERFORM, null);
    result.add(status);
    result.add(entry);
    return result;
  }
}
