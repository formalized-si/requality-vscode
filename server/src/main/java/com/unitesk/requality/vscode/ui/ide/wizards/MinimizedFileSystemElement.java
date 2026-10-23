package com.unitesk.requality.vscode.ui.ide.wizards;

import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.ide.dialogs.FileSystemElement;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.MinimizedFileSystemElement}: a file or folder of the import
 * tree, whose children are read from the structure provider on first use.
 */
public class MinimizedFileSystemElement extends FileSystemElement {
  private boolean populated;

  public MinimizedFileSystemElement(String label, FileSystemElement parent, boolean isDirectory) {
    super(label, parent, isDirectory);
    if (parent != null) {
      if (isDirectory)
        parent.getFolders().put(label, this);
      else
        parent.getFiles().put(label, this);
    }
  }

  /** Marks the element as populated (its children are the ones added by hand). */
  public void setPopulated() {
    populated = true;
  }

  /** The extension of the file name (without the dot), or the empty string. */
  public String getFileNameExtension() {
    String name = getLabel();
    int index = name.lastIndexOf('.');
    return index < 0 ? "" : name.substring(index + 1);
  }

  /** The non-folder children of this element, read by the provider. */
  public Children getFiles(IImportStructureProvider provider) {
    populate(provider);
    return new Children(this, false);
  }

  /** The folder children of this element, read by the provider. */
  public Children getFolders(IImportStructureProvider provider) {
    populate(provider);
    return new Children(this, true);
  }

  private void populate(IImportStructureProvider provider) {
    if (populated)
      return;
    populated = true;
    Object object = getFileSystemObject();
    if (object == null || !isDirectory())
      return;
    List<?> children = provider.getChildren(object);
    if (children == null)
      return;
    for (Object child : children) {
      MinimizedFileSystemElement element =
          new MinimizedFileSystemElement(provider.getLabel(child), this, provider.isFolder(child));
      element.setFileSystemObject(child);
    }
  }

  /** The files or the folders of an element. */
  public static final class Children {
    private final MinimizedFileSystemElement owner;
    private final boolean folders;

    private Children(MinimizedFileSystemElement owner, boolean folders) {
      this.owner = owner;
      this.folders = folders;
    }

    /** The children of the owner element (the parent must be the owner). */
    public Object[] getChildren(Object parent) {
      if (parent != owner)
        return new Object[0];
      Map<String, FileSystemElement> children = folders ? owner.getFolders() : owner.getFiles();
      return children.values().toArray();
    }
  }
}
