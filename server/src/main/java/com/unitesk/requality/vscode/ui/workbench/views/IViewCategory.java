package com.unitesk.requality.vscode.ui.workbench.views;

/** {@code org.eclipse.ui.views.IViewCategory}: a category of views (the folders of the Show View dialog). */
public interface IViewCategory {

  /** The id of the category. */
  String getId();

  /** The name of the category. */
  String getLabel();

  /** The views of the category. */
  IViewDescriptor[] getViews();
}
