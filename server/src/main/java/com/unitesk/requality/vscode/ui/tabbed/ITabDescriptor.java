package com.unitesk.requality.vscode.ui.tabbed;

/** {@code org.eclipse.ui.views.properties.tabbed.ITabDescriptor}: a tab of a tabbed property sheet and its sections. */
public interface ITabDescriptor {
  String getId();

  String getLabel();

  ISection[] getSections();
}
