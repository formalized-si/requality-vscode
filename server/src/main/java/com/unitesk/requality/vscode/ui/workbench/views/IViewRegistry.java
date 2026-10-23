package com.unitesk.requality.vscode.ui.workbench.views;

/** {@code org.eclipse.ui.views.IViewRegistry}: the views and the categories of the {@code org.eclipse.ui.views} extensions. */
public interface IViewRegistry {

  /** The categories of the views, in the order of plugin.xml. */
  IViewCategory[] getCategories();

  /** The views that can be shown, in the order of plugin.xml. */
  IViewDescriptor[] getViews();

  /** The view with the id, or null. */
  IViewDescriptor find(String id);
}
