package com.unitesk.requality.vscode.ui.resources;

import java.io.File;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;

/**
 * {@code org.eclipse.core.internal.resources.WorkspaceDescription}: the settings of the workspace, kept in the
 * preference store of the server storage ({@code org.eclipse.core.resources.prefs}) with the defaults of
 * {@code ResourcesPlugin}; the automatic build is the one of the {@link com.unitesk.requality.vscode.core.Workspace}.
 * The description is stateless: every setter writes the store, so {@code IWorkspace.setDescription} only applies it.
 */
final class WorkspaceDescription implements IWorkspaceDescription {
  static final String PREF_FILE_STATE_LONGEVITY = "fileStateLongevity";
  static final String PREF_MAX_FILE_STATES = "maxFileStates";
  static final String PREF_MAX_FILE_STATE_SIZE = "maxFileStateSize";
  static final String PREF_APPLY_FILE_STATE_POLICY = "applyFileStatePolicy";
  static final String PREF_KEEP_DERIVED_STATE = "keepDerivedState";
  static final String PREF_SNAPSHOT_INTERVAL = "snapshotInterval";
  static final String PREF_ENCODING = "encoding";
  static final long DAY = 86400000L;
  static final long MEGABYTE = 1024L * 1024;
  static final String PREF_BUILD_ORDER = "buildOrder";
  static final String PREF_MAX_BUILD_ITERATIONS = "maxBuildIterations";
  static final String PREF_MAX_CONCURRENT_BUILDS = "maxConcurrentBuilds";
  static final String DEFAULT_ENCODING = "UTF-8";

  private static final java.util.Map<File, PreferenceStore> STORES = new java.util.HashMap<>();

  /** The store of the workspace: kept in the server storage, in memory when the server has none. */
  private static synchronized PreferenceStore store() {
    File dir = ServerContext.server() != null ? ServerContext.server().options().storage : null;
    File file = dir == null ? null : new File(dir, "org.eclipse.core.resources.prefs");
    PreferenceStore cached = STORES.get(file);
    if (cached == null) {
      PreferenceStore s = file == null ? new PreferenceStore() : new PreferenceStore(file);
      s.setDefault(PREF_FILE_STATE_LONGEVITY, 7 * DAY);
      s.setDefault(PREF_MAX_FILE_STATES, 50);
      s.setDefault(PREF_MAX_FILE_STATE_SIZE, MEGABYTE);
      s.setDefault(PREF_APPLY_FILE_STATE_POLICY, true);
      s.setDefault(PREF_KEEP_DERIVED_STATE, false);
      s.setDefault(PREF_SNAPSHOT_INTERVAL, 5L * 60000);
      s.setDefault(PREF_ENCODING, DEFAULT_ENCODING);
      s.setDefault(PREF_MAX_BUILD_ITERATIONS, 10);
      s.setDefault(PREF_MAX_CONCURRENT_BUILDS, 1);
      STORES.put(file, s);
      cached = s;
    }
    return cached;
  }

  @Override
  public boolean isAutoBuilding() {
    return ServerContext.server() == null || ServerContext.server().workspace().isAutoBuilding();
  }

  @Override
  public void setAutoBuilding(boolean value) {
    if (ServerContext.server() != null) {
      ServerContext.server().workspace().setAutoBuilding(value);
      ServerContext.client().setContext("requality.autoBuilding", value);
    }
  }

  @Override
  public long getSnapshotInterval() {
    return store().getLong(PREF_SNAPSHOT_INTERVAL);
  }

  @Override
  public void setSnapshotInterval(long millis) {
    store().setValue(PREF_SNAPSHOT_INTERVAL, millis);
  }

  @Override
  public long getFileStateLongevity() {
    return store().getLong(PREF_FILE_STATE_LONGEVITY);
  }

  @Override
  public void setFileStateLongevity(long millis) {
    store().setValue(PREF_FILE_STATE_LONGEVITY, millis);
  }

  @Override
  public int getMaxFileStates() {
    return store().getInt(PREF_MAX_FILE_STATES);
  }

  @Override
  public void setMaxFileStates(int count) {
    store().setValue(PREF_MAX_FILE_STATES, count);
  }

  @Override
  public long getMaxFileStateSize() {
    return store().getLong(PREF_MAX_FILE_STATE_SIZE);
  }

  @Override
  public void setMaxFileStateSize(long bytes) {
    store().setValue(PREF_MAX_FILE_STATE_SIZE, bytes);
  }

  @Override
  public boolean isApplyFileStatePolicy() {
    return store().getBoolean(PREF_APPLY_FILE_STATE_POLICY);
  }

  @Override
  public void setApplyFileStatePolicy(boolean value) {
    store().setValue(PREF_APPLY_FILE_STATE_POLICY, value);
  }

  @Override
  public boolean isKeepDerivedState() {
    return store().getBoolean(PREF_KEEP_DERIVED_STATE);
  }

  @Override
  public void setKeepDerivedState(boolean value) {
    store().setValue(PREF_KEEP_DERIVED_STATE, value);
  }

  @Override
  public String getEncoding() {
    String v = store().getString(PREF_ENCODING);
    return v == null || v.isEmpty() ? DEFAULT_ENCODING : v;
  }

  @Override
  public void setEncoding(String charset) {
    store().setValue(PREF_ENCODING, charset == null || charset.isEmpty() ? DEFAULT_ENCODING : charset);
  }

  /** The build order is kept as one project name per line; the key is absent for the default order. */
  @Override
  public String[] getBuildOrder() {
    PreferenceStore s = store();
    if (!s.contains(PREF_BUILD_ORDER) || s.isDefault(PREF_BUILD_ORDER))
      return null;
    String v = s.getString(PREF_BUILD_ORDER);
    return v.isEmpty() ? new String[0] : v.split("\n", -1);
  }

  @Override
  public void setBuildOrder(String[] projectNames) {
    if (projectNames == null) {
      store().setToDefault(PREF_BUILD_ORDER);
      return;
    }
    store().setValue(PREF_BUILD_ORDER, String.join("\n", projectNames));
  }

  @Override
  public int getMaxBuildIterations() {
    return store().getInt(PREF_MAX_BUILD_ITERATIONS);
  }

  @Override
  public void setMaxBuildIterations(int count) {
    store().setValue(PREF_MAX_BUILD_ITERATIONS, count);
  }

  @Override
  public int getMaxConcurrentBuilds() {
    return store().getInt(PREF_MAX_CONCURRENT_BUILDS);
  }

  @Override
  public void setMaxConcurrentBuilds(int count) {
    store().setValue(PREF_MAX_CONCURRENT_BUILDS, count);
  }
}
