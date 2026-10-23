package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.unitesk.requality.vscode.ui.ide.wizards.IImportStructureProvider;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.ZipLeveledStructureProvider}: the folders and files of a zip archive as
 * the import structure. The root element is the archive itself (full path empty); the children of a folder are its
 * entries directly below it. Folders that have no entry of their own are made up from the entry names.
 */
public class ZipLeveledStructureProvider implements IImportStructureProvider {
  /** The name of the entry of the root, which has none. */
  public static final String ROOT = "";

  private final ZipFile zipFile;
  /** Folder path (no trailing slash, "" for the root) → the children entries (ZipEntry or a folder name). */
  private final Map<String, List<Object>> children = new LinkedHashMap<>();
  private final Map<String, ZipEntry> entries = new LinkedHashMap<>();

  public ZipLeveledStructureProvider(ZipFile zipFile) {
    this.zipFile = zipFile;
    Enumeration<? extends ZipEntry> all = zipFile.entries();
    while (all.hasMoreElements()) {
      ZipEntry e = all.nextElement();
      String name = e.getName();
      boolean dir = name.endsWith("/");
      String path = dir ? name.substring(0, name.length() - 1) : name;
      if (path.isEmpty())
        continue;
      entries.put(path, e);
      addToParents(path);
    }
  }

  /** Records the path as a child of its parent folder, and the missing parent folders as well. */
  private void addToParents(String path) {
    String parent = parentOf(path);
    List<Object> list = children.computeIfAbsent(parent, k -> new ArrayList<>());
    if (!list.contains(path))
      list.add(path);
    if (!parent.isEmpty() && !entries.containsKey(parent)) {
      entries.put(parent, null);
      addToParents(parent);
    }
  }

  private static String parentOf(String path) {
    int i = path.lastIndexOf('/');
    return i < 0 ? ROOT : path.substring(0, i);
  }

  /** The entry or folder name of the element: a path inside the archive, "" for the root. */
  @Override
  public String getFullPath(Object element) {
    return element == null ? ROOT : (String) element;
  }

  @Override
  public String getLabel(Object element) {
    String p = getFullPath(element);
    int i = p.lastIndexOf('/');
    return i < 0 ? p : p.substring(i + 1);
  }

  @Override
  public boolean isFolder(Object element) {
    String p = getFullPath(element);
    if (p.isEmpty())
      return true;
    ZipEntry e = entries.get(p);
    return e == null || e.isDirectory() || children.containsKey(p);
  }

  @Override
  public List<?> getChildren(Object element) {
    if (!isFolder(element))
      return null;
    List<Object> list = children.get(getFullPath(element));
    return list == null ? new ArrayList<>() : new ArrayList<>(list);
  }

  @Override
  public InputStream getContents(Object element) {
    ZipEntry e = entries.get(getFullPath(element));
    if (e == null || e.isDirectory())
      return null;
    try {
      return zipFile.getInputStream(e);
    } catch (IOException ex) {
      return null;
    }
  }

  /** The entry of the path (for the file contents and the modification times), or null. */
  public ZipEntry getEntry(Object element) {
    return entries.get(getFullPath(element));
  }
}
