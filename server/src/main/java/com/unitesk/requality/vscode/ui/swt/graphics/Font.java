package com.unitesk.requality.vscode.ui.swt.graphics;

import com.unitesk.requality.vscode.ui.swt.SWT;

/** {@code org.eclipse.swt.graphics.Font}: only the style (bold/italic) and a relative height are rendered. */
public final class Font extends Resource {
  private final FontData data;

  public Font(Object device, FontData fd) {
    this.data = fd;
  }

  public Font(Object device, FontData[] fds) {
    this.data = fds.length > 0 ? fds[0] : new FontData();
  }

  public Font(Object device, String name, int height, int style) {
    this.data = new FontData(name, height, style);
  }

  public FontData[] getFontData() {
    return new FontData[] { new FontData(data.getName(), data.getHeight(), data.getStyle()) };
  }

  public boolean isBold() {
    return SWT.has(data.getStyle(), SWT.BOLD);
  }

  public boolean isItalic() {
    return SWT.has(data.getStyle(), SWT.ITALIC);
  }

  public int getHeight() {
    return data.getHeight();
  }
}
