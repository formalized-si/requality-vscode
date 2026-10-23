package com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.impl;

import java.util.List;

import com.unitesk.requality.vscode.ui.e4.model.application.ui.AbstractUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.ElementList;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MElementContainer;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPartSashContainerElement;

/**
 * {@code org.eclipse.e4.ui.model.application.ui.basic.impl.PartStackImpl}: a stack of parts, i.e. one VS Code editor
 * group (tag "Editor" for the editor groups). The selected element is the shown one (VS Code shows one tab per group).
 */
public class PartStackImpl extends AbstractUIElement implements MPartSashContainerElement, MElementContainer<MUIElement> {
  private final List<MUIElement> children = new ElementList<>(this);
  private MUIElement selected;

  @Override
  public List<MUIElement> getChildren() {
    return children;
  }

  /** The shown element: the selected one while it is in the stack, else the first one. */
  public MUIElement getSelectedElement() {
    if (selected != null && children.contains(selected))
      return selected;
    return children.isEmpty() ? null : children.get(0);
  }

  public void setSelectedElement(MUIElement element) {
    selected = element;
  }
}
