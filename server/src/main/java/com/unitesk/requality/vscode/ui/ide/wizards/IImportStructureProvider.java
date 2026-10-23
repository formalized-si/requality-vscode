package com.unitesk.requality.vscode.ui.ide.wizards;

import java.io.InputStream;
import java.util.List;

/** {@code org.eclipse.ui.wizards.datatransfer.IImportStructureProvider}. */
public interface IImportStructureProvider {
  /** The children of a folder, or null if the element is not a folder. */
  List<?> getChildren(Object element);

  InputStream getContents(Object element);

  String getFullPath(Object element);

  String getLabel(Object element);

  boolean isFolder(Object element);
}
