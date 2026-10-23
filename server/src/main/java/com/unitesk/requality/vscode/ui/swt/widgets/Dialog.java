package com.unitesk.requality.vscode.ui.swt.widgets;

/** {@code org.eclipse.swt.widgets.Dialog}: base of the native SWT dialogs. */
public abstract class Dialog {
  private final Shell parent;
  private int style;
  private String title = "";

  public Dialog(Shell parent) {
    this(parent, 0);
  }

  public Dialog(Shell parent, int style) {
    this.parent = parent;
    this.style = style;
  }

  public Shell getParent() {
    return parent;
  }

  public int getStyle() {
    return style;
  }

  public String getText() {
    return title;
  }

  public void setText(String string) {
    title = string == null ? "" : string;
  }
}
