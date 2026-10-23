package com.unitesk.requality.vscode.ui.resources;

/**
 * {@code org.eclipse.core.resources.IWorkspaceDescription}: the settings of the workspace (the Workspace and Local
 * History pages of the preferences): automatic build, the workspace save interval, the local history of the files
 * (longevity, entries per file, size, policy, derived states) and the default text file encoding.
 */
public interface IWorkspaceDescription {
  /** Project &gt; Build Automatically. */
  boolean isAutoBuilding();

  void setAutoBuilding(boolean value);

  /** The save interval of the workspace in milliseconds. */
  long getSnapshotInterval();

  void setSnapshotInterval(long millis);

  /** How long the states of a file are kept, in milliseconds. */
  long getFileStateLongevity();

  void setFileStateLongevity(long millis);

  /** The maximum number of states kept per file. */
  int getMaxFileStates();

  void setMaxFileStates(int count);

  /** The maximum size of a file whose state is kept, in bytes. */
  long getMaxFileStateSize();

  void setMaxFileStateSize(long bytes);

  /** Whether the longevity, the number of states and the size limits are applied to the local history. */
  boolean isApplyFileStatePolicy();

  void setApplyFileStatePolicy(boolean value);

  boolean isKeepDerivedState();

  void setKeepDerivedState(boolean value);

  /** The default charset of the text files of the workspace (the encoding of a file without its own). */
  String getEncoding();

  void setEncoding(String charset);

  /**
   * The project build order ({@code IWorkspaceDescription.getBuildOrder}): the names of the projects, or null for the
   * default order (the order of the references between the projects).
   */
  String[] getBuildOrder();

  void setBuildOrder(String[] projectNames);

  /** The maximum number of iterations of the builds of projects with cycles. */
  int getMaxBuildIterations();

  void setMaxBuildIterations(int count);

  /** The maximum number of project builds that run at the same time. */
  int getMaxConcurrentBuilds();

  void setMaxConcurrentBuilds(int count);
}
