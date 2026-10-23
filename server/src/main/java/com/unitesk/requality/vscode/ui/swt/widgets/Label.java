package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.SWT;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.swt.widgets.Label} (also SEPARATOR). */
public class Label extends Control {
  private String text = "";
  private Image image;

  public Label(Composite parent, int style) {
    super(parent, style, "label");
    created();
  }

  public void setText(String text) {
    checkWidget();
    if (text == null)
      SWT.error(SWT.ERROR_NULL_ARGUMENT);
    this.text = text;
    set("text", this.text);
  }

  public String getText() {
    return text;
  }

  public void setImage(Image image) {
    checkWidget();
    this.image = image;
    set("image", image == null ? null : image.getPath());
  }

  public Image getImage() {
    return image;
  }

  public void setAlignment(int alignment) {
    set("align", alignment);
  }
}
