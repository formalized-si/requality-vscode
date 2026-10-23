package com.unitesk.requality.vscode.ui.ide.views;

import com.unitesk.requality.vscode.ui.resources.IMarker;

/**
 * {@code org.eclipse.ui.views.BookmarkView}: the bookmarks of the workspace (Description, Resource, Path, Location),
 * shown in the Requality panel.
 */
public class BookmarkView extends MarkerView {
  public static final String ID = "org.eclipse.ui.views.BookmarkView";

  public BookmarkView() {
    super(IMarker.BOOKMARK);
  }

  @Override
  protected String getViewId() {
    return ID;
  }

  @Override
  protected String getDefaultTitle() {
    return "Bookmarks";
  }
}
