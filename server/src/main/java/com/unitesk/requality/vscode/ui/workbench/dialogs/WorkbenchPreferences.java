package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.io.File;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;

/**
 * {@code org.eclipse.ui.internal.WorkbenchPlugin} preference store ({@code org.eclipse.ui.workbench}): the settings of
 * the General page of the preferences, kept in the server storage with the defaults of {@code WorkbenchPreferenceInitializer}.
 */
public final class WorkbenchPreferences {
  public static final String RUN_IN_BACKGROUND = "RUN_IN_BACKGROUND";
  /** {@code IWorkbenchPreferenceConstants.SHOW_SYSTEM_JOBS}: the Progress view shows the system operations (off). */
  public static final String SHOW_SYSTEM_JOBS = "SHOW_SYSTEM_JOBS";
  public static final String STICKY_CYCLE = "STICKY_CYCLE";
  public static final String OPEN_ON_SINGLE_CLICK = "OPEN_ON_SINGLE_CLICK";
  public static final String SELECT_ON_HOVER = "SELECT_ON_HOVER";
  public static final String OPEN_AFTER_DELAY = "OPEN_AFTER_DELAY";
  public static final String WORKBENCH_SAVE_INTERVAL = "WORKBENCH_SAVE_INTERVAL";
  public static final String LARGE_VIEW_LIMIT = "largeViewLimit";
  public static final String SHOW_MEMORY_MONITOR = "SHOW_MEMORY_MONITOR";
  public static final String RESOURCE_RENAME_MODE = "RESOURCE_RENAME_MODE";
  public static final String RESOURCE_RENAME_MODE_INLINE = "inline";
  public static final String RESOURCE_RENAME_MODE_DIALOG = "dialog";
  public static final String INITIALLY_SHOW_FILTER_TEXT_IN_MARKER_VIEWS = "INITIALLY_SHOW_FILTER_TEXT_IN_MARKER_VIEWS";
  /** {@code IPreferenceConstants.OPEN_PERSP_MODE}: where a perspective opens (Window &gt; Preferences &gt; Perspectives). */
  public static final String OPEN_PERSP_MODE = "OPEN_PERSPECTIVE_MODE";
  /** {@code IPreferenceConstants.OPM_ACTIVE_PAGE}: in the same window. */
  public static final int OPM_ACTIVE_PAGE = 0;
  /** {@code IPreferenceConstants.OPM_NEW_WINDOW}: in a new window (VS Code has one window: kept, not used). */
  public static final int OPM_NEW_WINDOW = 2;
  /** {@code IWorkbenchPreferenceConstants.DEFAULT_PERSPECTIVE_ID}: the id of the default perspective ("" for the product's). */
  public static final String DEFAULT_PERSPECTIVE_ID = "defaultPerspectiveId";
  /** The saved perspectives and the custom definitions of the predefined ones, as JSON (see PerspectiveRegistry). */
  public static final String PERSPECTIVE_DEFINITIONS = "PERSPECTIVE_DEFINITIONS";
  /** The customization of the perspectives (shortcuts and hidden menu items), as JSON by perspective id (see PerspectiveCustomization). */
  public static final String PERSPECTIVE_CUSTOMIZATIONS = "PERSPECTIVE_CUSTOMIZATIONS";

  private static final java.util.Map<File, PreferenceStore> STORES = new java.util.HashMap<>();

  private WorkbenchPreferences() {
  }

  /** The store of the workbench preferences; its defaults are the ones of {@code WorkbenchPreferenceInitializer}. */
  public static synchronized IPreferenceStore getStore() {
    File dir = ServerContext.server() != null ? ServerContext.server().options().storage : null;
    File file = dir == null ? null : new File(dir, "org.eclipse.ui.workbench.prefs");
    PreferenceStore cached = STORES.get(file);
    if (cached == null) {
      PreferenceStore s = file == null ? new PreferenceStore() : new PreferenceStore(file);
      s.setDefault(RUN_IN_BACKGROUND, true);
      s.setDefault(STICKY_CYCLE, false);
      s.setDefault(OPEN_ON_SINGLE_CLICK, false);
      s.setDefault(SELECT_ON_HOVER, false);
      s.setDefault(OPEN_AFTER_DELAY, false);
      s.setDefault(WORKBENCH_SAVE_INTERVAL, 5);
      s.setDefault(LARGE_VIEW_LIMIT, 1000);
      s.setDefault(SHOW_MEMORY_MONITOR, false);
      s.setDefault(RESOURCE_RENAME_MODE, RESOURCE_RENAME_MODE_INLINE);
      s.setDefault(INITIALLY_SHOW_FILTER_TEXT_IN_MARKER_VIEWS, true);
      s.setDefault(OPEN_PERSP_MODE, OPM_ACTIVE_PAGE);
      s.setDefault(DEFAULT_PERSPECTIVE_ID, "");
      s.setDefault(PERSPECTIVE_DEFINITIONS, "");
      s.setDefault(PERSPECTIVE_CUSTOMIZATIONS, "");
      STORES.put(file, s);
      cached = s;
    }
    return cached;
  }
}
