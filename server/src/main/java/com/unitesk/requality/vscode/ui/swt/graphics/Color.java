package com.unitesk.requality.vscode.ui.swt.graphics;

/**
 * {@code org.eclipse.swt.graphics.Color}. A color is either an explicit RGB value or a theme color (a VS Code CSS
 * variable), so that system colors follow the VS Code theme.
 */
public final class Color extends Resource {
  private final RGB rgb;
  private final String cssValue;

  public Color(Object device, int red, int green, int blue) {
    this.rgb = new RGB(red, green, blue);
    this.cssValue = rgb.css();
  }

  public Color(Object device, RGB rgb) {
    this.rgb = rgb;
    this.cssValue = rgb.css();
  }

  public Color(int red, int green, int blue) {
    this(null, red, green, blue);
  }

  /** A theme color. */
  public static Color theme(String cssValue, RGB fallback) {
    return new Color(cssValue, fallback);
  }

  private Color(String cssValue, RGB fallback) {
    this.rgb = fallback;
    this.cssValue = cssValue;
  }

  public RGB getRGB() {
    return rgb;
  }

  public int getRed() {
    return rgb.red;
  }

  public int getGreen() {
    return rgb.green;
  }

  public int getBlue() {
    return rgb.blue;
  }

  /** {@code org.eclipse.swt.graphics.Color#getAlpha()}: colors are opaque. */
  public int getAlpha() {
    return 255;
  }

  public String css() {
    return cssValue;
  }
}
