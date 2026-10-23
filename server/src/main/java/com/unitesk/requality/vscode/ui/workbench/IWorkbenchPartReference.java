package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IWorkbenchPartReference}. */
public interface IWorkbenchPartReference {
  IWorkbenchPart getPart(boolean restore);

  String getId();

  String getTitle();

  String getPartName();

  IWorkbenchPage getPage();
}
