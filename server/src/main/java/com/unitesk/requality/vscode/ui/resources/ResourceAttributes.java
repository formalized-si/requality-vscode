package com.unitesk.requality.vscode.ui.resources;

/** {@code org.eclipse.core.resources.ResourceAttributes}. */
public class ResourceAttributes {
  private boolean readOnly;
  private boolean executable;
  private boolean hidden;
  private boolean archive;
  private boolean symbolicLink;

  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public boolean isExecutable() {
    return executable;
  }

  public void setExecutable(boolean executable) {
    this.executable = executable;
  }

  public boolean isHidden() {
    return hidden;
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
  }

  /** {@code ResourceAttributes.isSymbolicLink}: the file or folder is a symbolic link (java.nio.file.Files). */
  public boolean isSymbolicLink() {
    return symbolicLink;
  }

  public void setSymbolicLink(boolean symbolicLink) {
    this.symbolicLink = symbolicLink;
  }

  public boolean isArchive() {
    return archive;
  }

  public void setArchive(boolean archive) {
    this.archive = archive;
  }
}
