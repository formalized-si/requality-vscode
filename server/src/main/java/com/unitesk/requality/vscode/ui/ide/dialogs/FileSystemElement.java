package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code org.eclipse.ui.dialogs.FileSystemElement}: a file or a folder of a file-system import tree. */
public class FileSystemElement {
  private final String label;
  private final FileSystemElement parent;
  private final boolean isDirectory;
  private Object fileSystemObject;
  private final Map<String, FileSystemElement> files = new LinkedHashMap<>();
  private final Map<String, FileSystemElement> folders = new LinkedHashMap<>();

  public FileSystemElement(String label, FileSystemElement parent, boolean isDirectory) {
    this.label = label;
    this.parent = parent;
    this.isDirectory = isDirectory;
  }

  /** The name of the element. */
  public String getLabel() {
    return label;
  }

  public FileSystemElement getParent() {
    return parent;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public Object getFileSystemObject() {
    return fileSystemObject;
  }

  public void setFileSystemObject(Object fileSystemObject) {
    this.fileSystemObject = fileSystemObject;
  }

  /** The (already known) non-folder children of this element. */
  public Map<String, FileSystemElement> getFiles() {
    return files;
  }

  /** The (already known) folder children of this element. */
  public Map<String, FileSystemElement> getFolders() {
    return folders;
  }

  @Override
  public String toString() {
    return label;
  }
}
