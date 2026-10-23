package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IFolderLayout}: a folder of a perspective layout, created by {@link IPageLayout#createFolder}. */
public interface IFolderLayout {
  /** {@code org.eclipse.ui.IFolderLayout#addView(String)}: a view of the folder, shown with the perspective. */
  void addView(String viewId);

  /** {@code org.eclipse.ui.IFolderLayout#addPlaceholder(String)}: a view of the folder that is not shown yet. */
  void addPlaceholder(String viewId);

  /** {@code org.eclipse.ui.IFolderLayout#getId()}. */
  String getId();
}
