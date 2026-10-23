package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

import com.unitesk.requality.vscode.ui.jface.viewers.ContentViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;

/**
 * {@code org.eclipse.ui.dialogs.PatternFilter}: the elements of a tree whose label matches the pattern of the filter
 * text. Every word of the pattern must match the start of a word of the label; {@code *} and {@code ?} are wildcards.
 * A parent is shown when one of its children is.
 */
public class PatternFilter extends ViewerFilter {
  private Pattern[] words = new Pattern[0];

  /** Sets the filter text (null or blank: every element is shown). */
  public void setPattern(String text) {
    String t = text == null ? "" : text.trim();
    words = t.isEmpty() ? new Pattern[0] : Arrays.stream(t.split("\\s+")).map(PatternFilter::wordPattern)
        .toArray(Pattern[]::new);
  }

  /** Whether a filter text is set. */
  public boolean hasPattern() {
    return words.length > 0;
  }

  @Override
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    return isElementVisible(viewer, element);
  }

  /** Whether the element, or one of its descendants, matches the filter text. */
  public boolean isElementVisible(Viewer viewer, Object element) {
    if (!hasPattern() || isLeafMatch(viewer, element))
      return true;
    if (viewer instanceof ContentViewer cv && cv.getContentProvider() instanceof ITreeContentProvider tree)
      for (Object child : tree.getChildren(element))
        if (isElementVisible(viewer, child))
          return true;
    return false;
  }

  /** Whether the label of the element matches the filter text. */
  protected boolean isLeafMatch(Viewer viewer, Object element) {
    if (viewer instanceof ContentViewer cv && cv.getLabelProvider() instanceof ILabelProvider label)
      return wordMatches(label.getText(element));
    return false;
  }

  /** Whether every word of the pattern matches the start of a word of the text. */
  public boolean wordMatches(String text) {
    if (text == null)
      return false;
    for (Pattern w : words)
      if (!w.matcher(text).find())
        return false;
    return true;
  }

  /** A word of the pattern: a start of a word of the text, with the wildcards {@code *} and {@code ?}. */
  private static Pattern wordPattern(String word) {
    StringBuilder sb = new StringBuilder("(?<![\\p{L}\\p{N}])");
    for (char c : word.toLowerCase(Locale.ROOT).toCharArray()) {
      if (c == '*')
        sb.append(".*");
      else if (c == '?')
        sb.append('.');
      else
        sb.append(Pattern.quote(String.valueOf(c)));
    }
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }
}
