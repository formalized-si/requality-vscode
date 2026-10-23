package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.io.File;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.MessageFormat;
import java.util.Date;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.IDEResourceInfoUtils}: the texts of the resource information page (type,
 * location, size and last modification of a resource on the file system).
 */
public final class IDEResourceInfoUtils {
  static final String NOT_EXIST_TEXT = "<resource does not exist>";
  static final String FILE_NOT_EXIST_TEXT = "{0} - (does not exist)";
  static final String UNKNOWN_LABEL = "Unknown";
  static final String BYTES_LABEL = "{0}  bytes";

  private IDEResourceInfoUtils() {
  }

  /** The type of the resource: File, Folder or Project. */
  public static String getTypeString(IResource resource) {
    switch (resource.getType()) {
      case IResource.FILE:
        return "File";
      case IResource.FOLDER:
        return "Folder";
      case IResource.PROJECT:
        return "Project";
      default:
        return UNKNOWN_LABEL;
    }
  }

  /** The location of the resource on the file system (the file or folder path), or the text for a missing one. */
  public static String getLocationText(IResource resource) {
    if (resource.getLocation() == null)
      return NOT_EXIST_TEXT;
    File f = resource.getLocation().toFile();
    if (!f.exists())
      return MessageFormat.format(FILE_NOT_EXIST_TEXT, f.getPath());
    return f.getAbsolutePath();
  }

  /** The size of a file in bytes; empty for the other resources. */
  public static String getSizeString(IResource resource) {
    if (!(resource instanceof IFile))
      return "";
    File f = resource.getLocation() == null ? null : resource.getLocation().toFile();
    if (f == null)
      return NOT_EXIST_TEXT;
    if (!f.exists())
      return NOT_EXIST_TEXT;
    return MessageFormat.format(BYTES_LABEL, NumberFormat.getInstance().format(f.length()));
  }

  /** The last modification of the resource, in the long date and medium time format. */
  public static String getDateStringValue(IResource resource) {
    File f = resource.getLocation() == null ? null : resource.getLocation().toFile();
    if (f == null)
      return NOT_EXIST_TEXT;
    if (!f.exists())
      return NOT_EXIST_TEXT;
    return DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM).format(new Date(f.lastModified()));
  }
}
