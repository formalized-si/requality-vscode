package com.unitesk.requality.vscode.ui.ide.wizards;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** {@code org.eclipse.ui.wizards.datatransfer.FileSystemStructureProvider}: the local file system (java.io.File). */
public class FileSystemStructureProvider implements IImportStructureProvider {
  public static final FileSystemStructureProvider INSTANCE = new FileSystemStructureProvider();

  @Override
  public List<?> getChildren(Object element) {
    File file = (File) element;
    if (!file.isDirectory())
      return null;
    File[] children = file.listFiles();
    List<File> result = new ArrayList<>();
    if (children != null)
      for (File child : children)
        result.add(child);
    return result;
  }

  @Override
  public InputStream getContents(Object element) {
    try {
      return new FileInputStream((File) element);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public String getFullPath(Object element) {
    return ((File) element).getAbsolutePath();
  }

  @Override
  public String getLabel(Object element) {
    String name = ((File) element).getName();
    return name.isEmpty() ? ((File) element).getAbsolutePath() : name;
  }

  @Override
  public boolean isFolder(Object element) {
    return ((File) element).isDirectory();
  }
}
