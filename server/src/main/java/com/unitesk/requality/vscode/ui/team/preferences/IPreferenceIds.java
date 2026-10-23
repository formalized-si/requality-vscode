package com.unitesk.requality.vscode.ui.team.preferences;

/** {@code org.eclipse.team.internal.ui.IPreferenceIds}: the keys of the Team UI preferences used by the Team pages. */
public interface IPreferenceIds {
  String PREFIX = "org.eclipse.team.ui.";
  String SYNCVIEW_DEFAULT_LAYOUT = PREFIX + "default_layout";
  String FLAT_LAYOUT = PREFIX + "flay_layout";
  String COMPRESSED_LAYOUT = PREFIX + "compressed_layout";
  String TREE_LAYOUT = PREFIX + "tree_layout";
  String SYNCVIEW_VIEW_SYNCINFO_IN_LABEL = PREFIX + "view_syncinfo_in_label";
  String SHOW_AUTHOR_IN_COMPARE_EDITOR = PREFIX + "show_author_in_compare_editor";
  String MAKE_FILE_WRITTABLE_IF_CONTEXT_MISSING = PREFIX + "validate_edit_with_no_context";
  String REUSE_OPEN_COMPARE_EDITOR = PREFIX + "reuse_open_compare_editor";
  String RUN_IMPORT_IN_BACKGROUND = PREFIX + "run_import_in_background_";
  /** The Group Revisions by Date mode of the local history page (no default: off). */
  String PREF_GROUPBYDATE_MODE = PREFIX + "group_bydate_mode";
  String APPLY_PATCH_IN_SYNCHRONIZE_VIEW = PREFIX + "apply_patch_in_sychronize_view";
  String SYNCVIEW_DEFAULT_PERSPECTIVE = PREFIX + "syncview_default_perspective";
  String SYNCVIEW_DEFAULT_PERSPECTIVE_NONE = PREFIX + "sync_view_perspective_none";
  String SYNCHRONIZING_COMPLETE_PERSPECTIVE = PREFIX + "sychronizing_default_perspective_to_show";
  /** {@code IPreferenceIds.SYNCVIEW_REMOVE_FROM_VIEW_NO_PROMPT}. */
  String SYNCVIEW_REMOVE_FROM_VIEW_NO_PROMPT = PREFIX + "remove_from_view_without_prompt";
  /** The Link with Editor state of the Synchronize view ({@code SynchronizeView.KEY_LINK_WITH_EDITOR}). */
  String SYNCVIEW_LINK_WITH_EDITOR = PREFIX + "syncview_link_with_editor";
}
