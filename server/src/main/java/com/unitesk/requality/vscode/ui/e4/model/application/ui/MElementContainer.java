package com.unitesk.requality.vscode.ui.e4.model.application.ui;

import java.util.List;

/** {@code org.eclipse.e4.ui.model.application.ui.MElementContainer}: an element holding child elements. */
public interface MElementContainer<T extends MUIElement> extends MUIElement {
  List<T> getChildren();
}
