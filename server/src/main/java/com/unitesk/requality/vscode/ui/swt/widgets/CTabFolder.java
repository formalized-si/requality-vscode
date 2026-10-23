package com.unitesk.requality.vscode.ui.swt.widgets;

/** {@code org.eclipse.swt.custom.CTabFolder}. */
public class CTabFolder extends TabFolder {
  public CTabFolder(Composite parent, int style) {
    super(parent, style);
  }

  public CTabItem getSelection2() {
    TabItem[] s = getSelection();
    return s.length == 0 ? null : (CTabItem) s[0];
  }

  public void setSimple(boolean b) {
  }

  public void setMaximizeVisible(boolean b) {
  }

  public void setMinimizeVisible(boolean b) {
  }
}
