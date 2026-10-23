package com.unitesk.requality.vscode.ui.jface.fieldassist;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.fieldassist.FieldDecoration}. */
public class FieldDecoration {
  private Image image;
  private String description;
  final String kind;

  public FieldDecoration(Image image, String description) {
    this(image, description, "info");
  }

  FieldDecoration(Image image, String description, String kind) {
    this.image = image;
    this.description = description;
    this.kind = kind;
  }

  public Image getImage() {
    return image;
  }

  public void setImage(Image image) {
    this.image = image;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
