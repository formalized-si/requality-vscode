package com.unitesk.requality.vscode.ui.swt.custom;

import com.unitesk.requality.vscode.ui.swt.graphics.Color;

/**
 * {@code org.eclipse.swt.custom.StyleRange}: the style of a range of characters of a {@link StyledText}. Only the
 * colors are used by the renderer; the font style is not supported.
 */
public class StyleRange {
  /** Offset of the first character of the range. */
  public int start;
  /** Number of characters in the range. */
  public int length;
  /** Foreground color, or null for the default. */
  public Color foreground;
  /** Background color, or null for the default. */
  public Color background;

  /** {@code org.eclipse.swt.custom.StyleRange#StyleRange(int, int, Color, Color)}. */
  public StyleRange(int start, int length, Color foreground, Color background) {
    this.start = start;
    this.length = length;
    this.foreground = foreground;
    this.background = background;
  }
}
