package com.unitesk.requality.vscode.ui.navigator.filters;

import java.util.regex.Pattern;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.UserFilter}: a name pattern of the "User filters" tab and whether
 * it is enabled; names are matched like {@code SearchPattern}: wildcards {@code *} and {@code ?}, else a prefix,
 * ignoring case (a pattern ending with a space or {@code <} must match exactly).
 */
public class UserFilter {
  private String regexp = "*.something";
  private boolean enabled = false;
  private Pattern searchPattern;

  public UserFilter() {
  }

  public UserFilter(String regexp, boolean enable) {
    this.regexp = regexp;
    this.enabled = enable;
  }

  public String getRegexp() {
    return regexp;
  }

  public void setRegexp(String newRegexp) {
    regexp = newRegexp;
    searchPattern = null;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enable) {
    enabled = enable;
  }

  public boolean matches(String string) {
    if (searchPattern == null)
      searchPattern = compile(regexp == null ? "" : regexp);
    return searchPattern.matcher(string).matches();
  }

  /**
   * SearchPattern.initializePatternAndMatchRule: with * or ? a pattern match, which ends with an implicit * unless the
   * pattern ends with *, a space or {@code <}; else an exact match for a trailing space or {@code <}, else a prefix.
   */
  private static Pattern compile(String p) {
    boolean wildcard = p.indexOf('*') >= 0 || p.indexOf('?') >= 0;
    boolean end = p.endsWith(" ") || p.endsWith("<");
    String body = end ? p.substring(0, p.length() - 1) : p;
    StringBuilder re = new StringBuilder();
    for (char c : body.toCharArray())
      re.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
    if (!end && !(wildcard && body.endsWith("*")))
      re.append(".*");
    return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  }
}
