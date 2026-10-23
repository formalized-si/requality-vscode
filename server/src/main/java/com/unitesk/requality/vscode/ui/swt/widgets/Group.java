package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.SWT;

/** {@code org.eclipse.swt.widgets.Group}. */
public class Group extends Composite {
  private String text = "";

  public Group(Composite parent, int style) {
    super(parent, style, "group");
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
}
