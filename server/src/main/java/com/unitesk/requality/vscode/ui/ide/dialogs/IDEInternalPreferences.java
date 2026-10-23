package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.io.File;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;

/**
 * {@code org.eclipse.ui.internal.ide.IDEInternalPreferences} and the preferences of {@code org.eclipse.core.resources}
 * that the Workspace page keeps outside the workspace description: the store of the IDE ({@code org.eclipse.ui.ide}),
 * kept in the server storage with the defaults of {@code IDEPreferenceInitializer} and of the resources plug-in.
 */
public final class IDEInternalPreferences {
  public static final String CLOSE_UNRELATED_PROJECTS = "CLOSE_UNRELATED_PROJECTS";
  public static final String SAVE_ALL_BEFORE_BUILD = "SAVE_ALL_BEFORE_BUILD";
  public static final String WORKSPACE_NAME = "WORKSPACE_NAME";
  /** The line delimiter of new text files: empty for the default of the platform, else the delimiter. */
  public static final String LINE_DELIMITER = "LINE_DELIMITER";
  /** {@code ResourcesPlugin.PREF_AUTO_REFRESH}: refresh using native hooks or polling. */
  public static final String PREF_AUTO_REFRESH = "refresh.enabled";
  /** {@code ResourcesPlugin.PREF_LIGHTWEIGHT_AUTO_REFRESH}: refresh on access. */
  public static final String PREF_LIGHTWEIGHT_AUTO_REFRESH = "refresh.lightweight.enabled";
  public static final String SHOW_LOCATION = "SHOW_LOCATION";
  public static final String SHOW_LOCATION_NAME = "SHOW_LOCATION_NAME";
  public static final String SHOW_PERSPECTIVE_IN_TITLE = "SHOW_PERSPECTIVE_IN_TITLE";
  public static final String SHOW_PRODUCT_IN_TITLE = "SHOW_PRODUCT_IN_TITLE";
  public static final String SHOW_ACTIVE_EDITOR_INFO_IN_TITLE = "SHOW_ACTIVE_EDITOR_INFO_IN_TITLE";
  /** Open referenced projects when a project is opened: always, never or prompt. */
  public static final String OPEN_REQUIRED_PROJECTS = "OPEN_REQUIRED_PROJECTS";
  public static final String PSPM_ALWAYS = "always";
  public static final String PSPM_NEVER = "never";
  public static final String PSPM_PROMPT = "prompt";
  /** The command for launching the system explorer. */
  public static final String WORKBENCH_SYSTEM_EXPLORER = "SYSTEM_EXPLORER";
  /** The severity of an unknown project nature or missing project encoding (-1: ignore). */
  public static final String MISSING_NATURE_SEVERITY = "MISSING_NATURE_MARKER_SEVERITY";
  public static final String MISSING_ENCODING_SEVERITY = "MISSING_ENCODING_MARKER_SEVERITY";
  public static final int SEVERITY_IGNORE = -1;
  /** The number of project builds that run at the same time (the Build page). */
  public static final String MAX_SIMULTANEOUS_BUILD = "MAX_CONCURRENT_PROJECT_BUILDS";
  public static final int MAX_MAX_SIMULTANEOUS_BUILD = 1000;
  /** {@code IDEInternalPreferences.MAX_SAVE_INTERVAL}: the largest workspace save interval, in minutes. */
  public static final int MAX_SAVE_INTERVAL = 9999;

  private static final java.util.Map<File, PreferenceStore> STORES = new java.util.HashMap<>();

  private IDEInternalPreferences() {
  }

  public static synchronized IPreferenceStore getStore() {
    File dir = ServerContext.server() != null ? ServerContext.server().options().storage : null;
    File file = dir == null ? null : new File(dir, "org.eclipse.ui.ide.prefs");
    PreferenceStore cached = STORES.get(file);
    if (cached == null) {
      PreferenceStore s = file == null ? new PreferenceStore() : new PreferenceStore(file);
      s.setDefault(CLOSE_UNRELATED_PROJECTS, false);
      s.setDefault(SAVE_ALL_BEFORE_BUILD, false);
      s.setDefault(WORKSPACE_NAME, "");
      s.setDefault(LINE_DELIMITER, "");
      s.setDefault(SHOW_LOCATION, false);
      s.setDefault(SHOW_LOCATION_NAME, true);
      s.setDefault(SHOW_PERSPECTIVE_IN_TITLE, false);
      s.setDefault(SHOW_PRODUCT_IN_TITLE, true);
      s.setDefault(SHOW_ACTIVE_EDITOR_INFO_IN_TITLE, true);
      s.setDefault(OPEN_REQUIRED_PROJECTS, PSPM_PROMPT);
      s.setDefault(WORKBENCH_SYSTEM_EXPLORER, "");
      s.setDefault(MISSING_NATURE_SEVERITY, 1);
      s.setDefault(MISSING_ENCODING_SEVERITY, 1);
      s.setDefault(MAX_SIMULTANEOUS_BUILD, 1);
      s.setDefault(PREF_AUTO_REFRESH, false);
      s.setDefault(PREF_LIGHTWEIGHT_AUTO_REFRESH, true);
      STORES.put(file, s);
      cached = s;
    }
    return cached;
  }
}
