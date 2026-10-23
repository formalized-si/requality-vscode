package com.unitesk.requality.vscode.ui.navigator.workingsets;

/**
 * {@code org.eclipse.ui.internal.navigator.workingsets.WorkingSetsContentProvider}: the content extension that shows
 * working sets as the top level elements of a navigator.
 */
// VSCODE: the Requality Explorer does not bind this content extension (its viewerContentBinding does not include
// org.eclipse.ui.navigator.resources.workingSets), so, as in Eclipse, no content of the explorer provides the
// children of a working set input; only the identifiers used by the working set actions are needed.
public final class WorkingSetsContentProvider {
  /** The id of the working sets content extension. */
  public static final String EXTENSION_ID = "org.eclipse.ui.navigator.resources.workingSets";
  /** The state model property: working sets are the top level elements. */
  public static final String SHOW_TOP_LEVEL_WORKING_SETS = EXTENSION_ID + ".showTopLevelWorkingSets";

  private WorkingSetsContentProvider() {
  }
}
