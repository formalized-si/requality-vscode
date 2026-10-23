package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.IStructuredContentProvider}. */
public interface IStructuredContentProvider extends IContentProvider {
  Object[] getElements(Object inputElement);
}
