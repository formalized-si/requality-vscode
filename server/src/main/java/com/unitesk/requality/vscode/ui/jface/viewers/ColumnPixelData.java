package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ColumnPixelData}. */
public class ColumnPixelData extends ColumnLayoutData {
  public int width;

  public ColumnPixelData(int width) {
    this.width = width;
  }

  public ColumnPixelData(int width, boolean resizable) {
    this.width = width;
    this.resizable = resizable;
  }
}
