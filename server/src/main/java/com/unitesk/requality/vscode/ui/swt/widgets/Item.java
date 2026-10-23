package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.SWT;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.swt.widgets.Item}. */
public abstract class Item extends Widget {
  final Widget owner;
  private String text = "";
  private Image image;

  protected Item(Widget owner, int style, String kind) {
    super(owner, style, kind);
    this.owner = owner;
  }

  @Override
  Widget getParentWidget() {
    return owner;
  }

  @Override
  Shell getShellInternal() {
    return owner.getShellInternal();
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
}
