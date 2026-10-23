package com.unitesk.requality.vscode.ui.e4.model.application.ui;

import java.util.ArrayList;
import java.util.List;

/** The parent and the tags of an element of the E4 model (the EMF features of {@code MUIElement}). */
public abstract class AbstractUIElement implements MUIElement {
  private MElementContainer<MUIElement> parent;
  private final List<String> tags = new ArrayList<>();

  @Override
  public MElementContainer<MUIElement> getParent() {
    return parent;
  }

  @Override
  public void setParent(MElementContainer<MUIElement> parent) {
    this.parent = parent;
  }

  @Override
  public List<String> getTags() {
    return tags;
  }

  @Override
  public boolean isVisible() {
    return true;
  }
}
