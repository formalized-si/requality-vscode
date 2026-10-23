package com.unitesk.requality.vscode.ui.e4.model.application.ui;

import java.util.List;

/** {@code org.eclipse.e4.ui.model.application.ui.MUIElement}: an element of the E4 model of the editor area. */
public interface MUIElement {
  MElementContainer<MUIElement> getParent();

  void setParent(MElementContainer<MUIElement> parent);

  List<String> getTags();

  boolean isVisible();
}
