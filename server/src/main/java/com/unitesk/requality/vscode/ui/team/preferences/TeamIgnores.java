package com.unitesk.requality.vscode.ui.team.preferences;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code org.eclipse.team.core.Team} global ignores ({@code getAllIgnores}, {@code setAllIgnores},
 * {@code getDefaultIgnores}): the patterns of the resources excluded from version control, kept in the Team UI store
 * ({@code ignore_files}: a pattern and its enabled flag, one per line). The defaults come from the extensions of the
 * {@code org.eclipse.team.core.ignore} point, of which the port has none.
 */
public final class TeamIgnores {
  /** A global ignore pattern with its enabled flag ({@code IIgnoreInfo}). */
  public static final class Info {
    private final String pattern;
    private final boolean enabled;

    public Info(String pattern, boolean enabled) {
      this.pattern = pattern;
      this.enabled = enabled;
    }

    public String getPattern() {
      return pattern;
    }

    public boolean getEnabled() {
      return enabled;
    }
  }

  private static final String PREF_TEAM_IGNORES = "ignore_files";
  private static final String SEPARATOR = "\n";

  private TeamIgnores() {
  }

  /** The global ignores, in the order of their patterns. */
  public static Info[] getAllIgnores() {
    List<Info> r = new ArrayList<>();
    String value = TeamUIPlugin.getPreferenceStore().getString(PREF_TEAM_IGNORES);
    if (value != null && !value.isEmpty()) {
      String[] parts = value.split(SEPARATOR, -1);
      for (int i = 0; i + 1 < parts.length; i += 2)
        if (!parts[i].isEmpty())
          r.add(new Info(parts[i], Boolean.parseBoolean(parts[i + 1])));
    }
    return r.toArray(new Info[0]);
  }

  /** The global ignores of the set: the patterns and their enabled flags. */
  public static void setAllIgnores(String[] patterns, boolean[] enabled) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < patterns.length; i++)
      buf.append(patterns[i]).append(SEPARATOR).append(enabled[i]).append(SEPARATOR);
    TeamUIPlugin.getPreferenceStore().setValue(PREF_TEAM_IGNORES, buf.toString());
  }

  /** The ignores of the extensions of the platform: none in the port. */
  public static Info[] getDefaultIgnores() {
    return new Info[0];
  }
}
