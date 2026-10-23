package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.compare.ITypedElement}. */
public interface ITypedElement {
  String FOLDER_TYPE = "*folder*";
  String UNKNOWN_TYPE = "";
  String TEXT_TYPE = "txt";

  String getName();

  Image getImage();

  String getType();
}
