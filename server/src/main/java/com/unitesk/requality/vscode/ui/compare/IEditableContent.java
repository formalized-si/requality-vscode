package com.unitesk.requality.vscode.ui.compare;

/** {@code org.eclipse.compare.IEditableContent}: a compare element whose contents can be changed. */
public interface IEditableContent {
  boolean isEditable();

  void setContent(byte[] newContent);

  ITypedElement replace(ITypedElement dest, ITypedElement src);
}
