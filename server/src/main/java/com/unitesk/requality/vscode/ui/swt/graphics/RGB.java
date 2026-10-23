package com.unitesk.requality.vscode.ui.swt.graphics;

/** {@code org.eclipse.swt.graphics.RGB}. */
public final class RGB {
  public int red;
  public int green;
  public int blue;

  public RGB(int red, int green, int blue) {
    this.red = red;
    this.green = green;
    this.blue = blue;
  }

  public String css() {
    return String.format("#%02x%02x%02x", red & 0xff, green & 0xff, blue & 0xff);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RGB && ((RGB) o).red == red && ((RGB) o).green == green && ((RGB) o).blue == blue;
  }

  @Override
  public int hashCode() {
    return (red << 16) | (green << 8) | blue;
  }
}
