package com.unitesk.requality.vscode.ui.compare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * An external file or folder of the file system chosen for a compare (the "External file" / "External folder" of
 * "Compare With Other Resource"): a read-only typed element whose type is its extension (FOLDER_TYPE for a folder, whose
 * files and folders are its children in a structure compare, matched by name).
 * VSCODE: Eclipse links the external files and folders into a hidden project and compares them as resources; the
 * workspace emulation has no links, so they are compared as these elements.
 */
public class ExternalFileTypedElement implements ITypedElement, IStreamContentAccessor,
    com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IStructureComparator {
  private final File fFile;

  public ExternalFileTypedElement(File file) {
    fFile = file;
  }

  public File getFile() {
    return fFile;
  }

  @Override
  public String getName() {
    return fFile.getName();
  }

  @Override
  public Image getImage() {
    return CompareUI.getImage(getType());
  }

  @Override
  public String getType() {
    if (fFile.isDirectory())
      return FOLDER_TYPE;
    String ext = CompareUI.extension(fFile.getName());
    return ext.isEmpty() ? UNKNOWN_TYPE : ext;
  }

  /** The files and folders of an external folder (none for a file). */
  @Override
  public Object[] getChildren() {
    File[] files = fFile.isDirectory() ? fFile.listFiles() : null;
    if (files == null)
      return new Object[0];
    java.util.Arrays.sort(files);
    Object[] r = new Object[files.length];
    for (int i = 0; i < files.length; i++)
      r[i] = new ExternalFileTypedElement(files[i]);
    return r;
  }

  /** Elements are matched by name in a structure compare (as ResourceNode). */
  @Override
  public boolean equals(Object other) {
    return other instanceof ITypedElement && getName().equals(((ITypedElement) other).getName());
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public InputStream getContents() throws CoreException {
    if (fFile.isDirectory())
      return null;
    try {
      return new FileInputStream(fFile);
    } catch (FileNotFoundException e) {
      throw new CoreException(new Status(IStatus.ERROR, CompareUI.PLUGIN_ID, 0, e.getMessage(), e));
    }
  }
}
