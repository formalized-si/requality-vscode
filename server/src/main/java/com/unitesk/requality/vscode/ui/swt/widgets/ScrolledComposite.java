package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.graphics.Point;

/** {@code org.eclipse.swt.custom.ScrolledComposite}: a scrollable container showing its content control. */
public class ScrolledComposite extends Composite {
  private Control content;
  private boolean expandHorizontal;
  private boolean expandVertical;

  public ScrolledComposite(Composite parent, int style) {
    super(parent, style, "scrolled");
    created();
  }

  public void setContent(Control content) {
    this.content = content;
    set("content", content == null ? null : content.id);
  }

  public Control getContent() {
    return content;
  }

  public void setExpandHorizontal(boolean b) {
    expandHorizontal = b;
    set("expandH", b);
  }

  public boolean getExpandHorizontal() {
    return expandHorizontal;
  }

  public void setExpandVertical(boolean b) {
    expandVertical = b;
    set("expandV", b);
  }

  public boolean getExpandVertical() {
    return expandVertical;
  }

  public void setMinSize(int w, int h) {
  }

  public void setMinSize(Point p) {
  }

  public void setMinWidth(int w) {
  }

  public void setMinHeight(int h) {
  }

  public void setAlwaysShowScrollBars(boolean b) {
  }

  public void setShowFocusedControl(boolean b) {
  }

  public void setOrigin(int x, int y) {
  }

  public Point getOrigin() {
    return new Point(0, 0);
  }
}
