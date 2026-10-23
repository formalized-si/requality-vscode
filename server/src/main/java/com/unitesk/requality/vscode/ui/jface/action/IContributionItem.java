package com.unitesk.requality.vscode.ui.jface.action;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolBar;

/** {@code org.eclipse.jface.action.IContributionItem}. */
public interface IContributionItem {
  default void fill(Menu menu, int index) {
  }

  default void fill(Composite parent) {
  }

  /** {@code fill(ToolBar, int)}: the item as a button of a view toolbar (a ToolItem at index, -1 appends). */
  default void fill(ToolBar parent, int index) {
  }

  default String getId() {
    return null;
  }

  default boolean isVisible() {
    return true;
  }

  default boolean isSeparator() {
    return false;
  }

  /** {@code isGroupMarker()}: separators and group markers start a named group of a contribution manager. */
  default boolean isGroupMarker() {
    return false;
  }

  default boolean isDynamic() {
    return false;
  }

  default void dispose() {
  }
}
