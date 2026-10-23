package com.unitesk.requality.vscode.ui.e4.model.application.ui.basic;

import java.util.List;

import com.unitesk.requality.vscode.ui.e4.model.application.ui.AbstractUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.ElementList;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MElementContainer;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MUIElement;

/**
 * {@code org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer}: the editor area, whose children are the
 * editor groups (one {@link impl.PartStackImpl} per VS Code editor group) placed from left to right.
 */
public class MPartSashContainer extends AbstractUIElement implements MPartSashContainerElement, MElementContainer<MUIElement> {
  private final List<MUIElement> children = new ElementList<>(this);

  @Override
  public List<MUIElement> getChildren() {
    return children;
  }
}
