package com.unitesk.requality.vscode.ui.team.preferences;

import java.io.File;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;

/**
 * {@code org.eclipse.team.internal.ui.TeamUIPlugin}: the preference store of the Team UI ({@code org.eclipse.team.ui}),
 * kept in the server storage with the defaults of the plug-in.
 */
public final class TeamUIPlugin {
  /** The perspective of the synchronize view by default (the id of the Team Synchronizing perspective). */
  public static final String TEAM_SYNCHRONIZING_PERSPECTIVE = "org.eclipse.team.ui.TeamSynchronizingPerspective";
  /** {@code MessageDialogWithToggle.PROMPT} (the value of the perspective switch setting). */
  public static final String PROMPT = "prompt";

  private static final java.util.Map<File, PreferenceStore> STORES = new java.util.HashMap<>();

  private TeamUIPlugin() {
  }

  public static synchronized IPreferenceStore getPreferenceStore() {
    File dir = ServerContext.server() != null ? ServerContext.server().options().storage : null;
    File file = dir == null ? null : new File(dir, "org.eclipse.team.ui.prefs");
    PreferenceStore cached = STORES.get(file);
    if (cached == null) {
      PreferenceStore s = file == null ? new PreferenceStore() : new PreferenceStore(file);
      s.setDefault(IPreferenceIds.SYNCVIEW_VIEW_SYNCINFO_IN_LABEL, false);
      s.setDefault(IPreferenceIds.SHOW_AUTHOR_IN_COMPARE_EDITOR, false);
      s.setDefault(IPreferenceIds.MAKE_FILE_WRITTABLE_IF_CONTEXT_MISSING, false);
      s.setDefault(IPreferenceIds.REUSE_OPEN_COMPARE_EDITOR, true);
      s.setDefault(IPreferenceIds.RUN_IMPORT_IN_BACKGROUND, false);
      s.setDefault(IPreferenceIds.APPLY_PATCH_IN_SYNCHRONIZE_VIEW, false);
      s.setDefault(IPreferenceIds.SYNCVIEW_DEFAULT_LAYOUT, IPreferenceIds.COMPRESSED_LAYOUT);
      s.setDefault(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE, TEAM_SYNCHRONIZING_PERSPECTIVE);
      s.setDefault(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE, PROMPT);
      s.setDefault(IPreferenceIds.SYNCVIEW_REMOVE_FROM_VIEW_NO_PROMPT, false);
      STORES.put(file, s);
      cached = s;
    }
    return cached;
  }
}
