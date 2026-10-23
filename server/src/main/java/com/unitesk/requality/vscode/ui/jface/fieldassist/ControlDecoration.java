package com.unitesk.requality.vscode.ui.jface.fieldassist;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.jface.fieldassist.ControlDecoration}: shown as an error/warning outline with the description
 * as tooltip on the decorated control.
 */
public class ControlDecoration {
  private final Control control;
  private String description = "";
  private String kind = "error";
  private boolean visible = true;
  private Image image;

  public ControlDecoration(Control control, int position) {
    this.control = control;
    update();
  }

  public ControlDecoration(Control control, int position, Object composite) {
    this(control, position);
  }

  public Control getControl() {
    return control;
  }

  public void setImage(Image image) {
    this.image = image;
  }

  public Image getImage() {
    return image;
  }

  /** Extension: the decoration kind (error, warning, info) of a registry decoration. */
  public void setFieldDecoration(FieldDecoration d) {
    kind = d.kind;
    if (d.getDescription() != null && !d.getDescription().isEmpty())
      description = d.getDescription();
    update();
  }

  public void setDescriptionText(String text) {
    description = text == null ? "" : text;
    update();
  }

  public String getDescriptionText() {
    return description;
  }

  public void show() {
    visible = true;
    update();
  }

  public void hide() {
    visible = false;
    update();
  }

  public boolean isVisible() {
    return visible;
  }

  public void setShowOnlyOnFocus(boolean b) {
  }

  public void setShowHover(boolean b) {
  }

  public void setMarginWidth(int w) {
  }

  public void showHoverText(String text) {
    setDescriptionText(text);
  }

  public void hideHover() {
  }

  public void dispose() {
    visible = false;
    update();
  }

  private void update() {
    if (control == null || control.isDisposed())
      return;
    if (!visible) {
      control.setDecoration(null);
      return;
    }
    JsonObject o = new JsonObject();
    o.addProperty("kind", kind);
    o.addProperty("text", description);
    control.setDecoration(o);
  }
}
