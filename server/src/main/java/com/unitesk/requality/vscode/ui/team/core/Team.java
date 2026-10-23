package com.unitesk.requality.vscode.ui.team.core;

/**
 * {@code org.eclipse.team.core.Team}: the content types of the files for version control (text or binary) and the
 * file content manager that holds their mappings.
 */
public final class Team {
  /** The content of a file is not known (no mapping). */
  public static final int UNKNOWN = 0;
  /** The file is text (ASCII) and is merged line by line. */
  public static final int TEXT = 1;
  /** The file is binary and is compared and replaced as a whole. */
  public static final int BINARY = 2;

  private static FileContentManager fileContentManager;

  private Team() {
  }

  /** {@code Team.getFileContentManager}: the file content manager of the team core. */
  public static synchronized FileContentManager getFileContentManager() {
    if (fileContentManager == null)
      fileContentManager = new FileContentManager();
    return fileContentManager;
  }
}
