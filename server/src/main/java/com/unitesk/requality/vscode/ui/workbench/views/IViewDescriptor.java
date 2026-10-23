package com.unitesk.requality.vscode.ui.workbench.views;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;

/** {@code org.eclipse.ui.views.IViewDescriptor}: a view of the {@code org.eclipse.ui.views} extension point. */
public interface IViewDescriptor {

  /** The id of the view. */
  String getId();

  /** The name of the view. */
  String getLabel();

  /** The description of the view (empty if none). */
  String getDescription();

  /** The icon of the view, or null. */
  ImageDescriptor getImageDescriptor();

  /** The ids of the categories the view is in, from the outermost; null if the view has no category. */
  String[] getCategoryPath();
}
