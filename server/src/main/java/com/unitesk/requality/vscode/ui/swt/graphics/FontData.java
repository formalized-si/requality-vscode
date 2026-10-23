package com.unitesk.requality.vscode.ui.swt.graphics;

/** {@code org.eclipse.swt.graphics.FontData}. */
public final class FontData {
  private String name;
  private int height;
  private int style;

  public FontData() {
    this("", 10, 0);
  }

  /** {@code FontData(String)}: the "1|name|height|style|..." form of {@link #toString()}. */
  public FontData(String string) {
    this();
    String[] p = string == null ? new String[0] : string.split("\\|");
    try {
      if (p.length > 1)
        name = p[1];
      if (p.length > 2)
        height = (int) Math.round(Double.parseDouble(p[2]));
      if (p.length > 3)
        style = Integer.parseInt(p[3]);
    } catch (NumberFormatException e) {
      // keep the defaults
    }
  }

  @Override
  public String toString() {
    return "1|" + name + "|" + height + "|" + style + "|";
  }

  public FontData(String name, int height, int style) {
    this.name = name;
    this.height = height;
    this.style = style;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public int getStyle() {
    return style;
  }

  public void setStyle(int style) {
    this.style = style;
  }
}
