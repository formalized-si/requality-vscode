package com.unitesk.requality.vscode.ui.resources;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The lock objects of the projects ({@code ResourceStorage.markerProjectLocks}): {@code get} gives the lock of a project,
 * created atomically on its first use. The plug-in's {@code ResourceStorage.getLockObject} reads the map and puts a new
 * lock when it finds none; the RPC, UI and job threads of the server ask for the lock of a project at the same time, so
 * an unsynchronized map could give two threads two locks for one project (and corrupt itself). With this map the read
 * already creates the one lock and the put of the plug-in never happens.
 */
public final class ProjectLocks extends ConcurrentHashMap<IProject, Object> {
  private static final long serialVersionUID = 1L;
  /** The lock of a null project (a HashMap accepts the null key; this map does not). */
  private static final Object NULL_PROJECT_LOCK = new Object();

  @Override
  public Object get(Object key) {
    if (!(key instanceof IProject))
      return key == null ? NULL_PROJECT_LOCK : super.get(key);
    return computeIfAbsent((IProject) key, p -> new Object());
  }

  @Override
  public Object put(IProject key, Object value) {
    return key == null ? NULL_PROJECT_LOCK : super.put(key, value);
  }
}
