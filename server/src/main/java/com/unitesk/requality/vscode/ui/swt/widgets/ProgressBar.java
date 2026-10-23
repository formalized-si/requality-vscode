package com.unitesk.requality.vscode.ui.swt.widgets;

/** {@code org.eclipse.swt.widgets.ProgressBar}. */
public class ProgressBar extends Control {
  private int minimum, maximum = 100, selection;

  public ProgressBar(Composite parent, int style) {
    super(parent, style, "progress");
    send();
    created();
  }

  private void send() {
    set("value", new int[] { selection, minimum, maximum });
  }

  public void setMinimum(int v) {
    minimum = v;
    send();
  }

  public void setMaximum(int v) {
    maximum = v;
    send();
  }

  public void setSelection(int v) {
    selection = v;
    send();
  }

  public int getSelection() {
    return selection;
  }

  public int getMaximum() {
    return maximum;
  }

  public int getMinimum() {
    return minimum;
  }
}
