package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code org.eclipse.ui.IWorkbenchPart}: a view or editor. The VS Code views (Requality Explorer, Links Explorer,
 * Outline) and the web editors are parts too; {@link #getFSId()} is the selection source they registered in the core
 * SelectionManager.
 */
public interface IWorkbenchPart extends IAdaptable {
  int PROP_TITLE = 0x001;
  int PROP_DIRTY = 0x101;
  int PROP_INPUT = 0x102;

  /** The view or editor id. */
  String getId();

  String getTitle();

  default FSId getFSId() {
    return null;
  }

  default IWorkbenchPartSite getSite() {
    return null;
  }

  default Image getTitleImage() {
    return null;
  }

  default String getTitleToolTip() {
    return getTitle();
  }

  default void createPartControl(Composite parent) {
  }

  default void setFocus() {
  }

  default void dispose() {
  }

  default void addPropertyListener(IPropertyListener listener) {
  }

  default void removePropertyListener(IPropertyListener listener) {
  }
}
