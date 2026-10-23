package com.unitesk.requality.vscode.ui.swt.custom;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.swt.custom.StyledText}. The widget is a multi-line text area (kind {@code text}) in the
 * browser; the styles are sent as the {@code styles} property ({@code start}, {@code length}, {@code background}
 * CSS color) so that the renderer can highlight the ranges. Only the methods used by the Requality views are
 * provided.
 */
public class StyledText extends Text {
  private final List<StyleRange> styles = new ArrayList<>();

  public StyledText(Composite parent, int style) {
    super(parent, style, "styledtext");
  }

  /**
   * {@code org.eclipse.swt.custom.StyledText#setStyleRange(StyleRange)}: sets the style of the range, replacing the
   * styles that overlap it; a null range removes all styles.
   */
  public void setStyleRange(StyleRange range) {
    checkWidget();
    if (range == null) {
      styles.clear();
    } else {
      int end = range.start + range.length;
      styles.removeIf(s -> s.start < end && range.start < s.start + s.length);
      styles.add(range);
    }
    sendStyles();
  }

  /** Replaces all the styles by the given ranges (one update for the whole list). */
  public void setStyleRanges(List<StyleRange> ranges) {
    checkWidget();
    styles.clear();
    styles.addAll(ranges);
    sendStyles();
  }

  /** The styles currently set, in the order they were set. */
  public List<StyleRange> getStyleRanges() {
    return new ArrayList<>(styles);
  }

  private void sendStyles() {
    List<List<Object>> sent = new ArrayList<>();
    for (StyleRange s : styles) {
      List<Object> r = new ArrayList<>();
      r.add(s.start);
      r.add(s.length);
      r.add(s.background == null ? null : s.background.css());
      sent.add(r);
    }
    set("styles", sent);
  }
}
