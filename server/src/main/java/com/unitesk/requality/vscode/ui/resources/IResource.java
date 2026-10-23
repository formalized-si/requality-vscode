package com.unitesk.requality.vscode.ui.resources;

import java.net.URI;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.ISchedulingRule;
import com.unitesk.requality.vscode.ui.runtime.QualifiedName;

/**
 * {@code org.eclipse.core.resources.IResource} over the file system: a file, folder or project of the VS Code
 * workspace. Full paths are {@code /<project>/<relative path>} as in Eclipse.
 */
public interface IResource extends IAdaptable, ISchedulingRule {
  int FILE = 0x1;
  int FOLDER = 0x2;
  int PROJECT = 0x4;
  int ROOT = 0x8;
  int DEPTH_ZERO = 0;
  int DEPTH_ONE = 1;
  int DEPTH_INFINITE = 2;
  int NONE = 0;
  int FORCE = 0x1;
  int KEEP_HISTORY = 0x2;
  int ALWAYS_DELETE_PROJECT_CONTENT = 0x4;
  int NEVER_DELETE_PROJECT_CONTENT = 0x8;
  int DERIVED = 0x400;
  int HIDDEN = 0x1000;
  int SHALLOW = 0x10;
  int REPLACE = 0x80;
  int BACKGROUND_REFRESH = 0x800;

  String getName();

  int getType();

  IPath getFullPath();

  IPath getLocation();

  URI getLocationURI();

  IPath getProjectRelativePath();

  IPath getRawLocation();

  IProject getProject();

  IContainer getParent();

  IWorkspace getWorkspace();

  boolean exists();

  boolean isAccessible();

  String getFileExtension();

  long getLocalTimeStamp();

  /** {@code IResource.NULL_STAMP}: the stamp of a resource that does not exist. */
  long NULL_STAMP = -1;

  /**
   * {@code IResource.setLocalTimeStamp(long)}: sets the time stamp of the resource in the file system (its last
   * modification time) and returns the time stamp it got.
   */
  default long setLocalTimeStamp(long value) throws CoreException {
    return getLocalTimeStamp();
  }

  boolean isSynchronized(int depth);

  boolean isReadOnly();

  boolean isHidden();

  boolean isDerived();

  boolean isLinked();

  /** {@code IResource.isVirtual}: the workspace emulation has no virtual folders. */
  default boolean isVirtual() {
    return false;
  }

  ResourceAttributes getResourceAttributes();

  void setResourceAttributes(ResourceAttributes attributes) throws CoreException;

  void refreshLocal(int depth, IProgressMonitor monitor) throws CoreException;

  /** {@code IResource.getPersistentProperty}: the value kept for the name (null when none), see ResourceProperties. */
  String getPersistentProperty(QualifiedName key) throws CoreException;

  /** {@code IResource.setPersistentProperty}: keeps the value for the name (null removes it). */
  void setPersistentProperty(QualifiedName key, String value) throws CoreException;

  void delete(boolean force, IProgressMonitor monitor) throws CoreException;

  void delete(int updateFlags, IProgressMonitor monitor) throws CoreException;

  void copy(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException;

  void copy(IPath destination, int updateFlags, IProgressMonitor monitor) throws CoreException;

  void move(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException;

  void move(IPath destination, int updateFlags, IProgressMonitor monitor) throws CoreException;

  void touch(IProgressMonitor monitor) throws CoreException;

  default IMarker createMarker(String type) throws CoreException {
    return MarkerStore.create(this, type);
  }

  /** {@code IResource.findMaxProblemSeverity}: the highest severity of the markers of the type, or -1. */
  default int findMaxProblemSeverity(String type, boolean includeSubtypes, int depth) throws CoreException {
    int max = -1;
    for (IMarker m : findMarkers(type, includeSubtypes, depth))
      max = Math.max(max, m.getAttribute(IMarker.SEVERITY, -1));
    return max;
  }

  default IMarker[] findMarkers(String type, boolean includeSubtypes, int depth) throws CoreException {
    return MarkerStore.find(this, type, includeSubtypes, depth);
  }

  default void deleteMarkers(String type, boolean includeSubtypes, int depth) throws CoreException {
    MarkerStore.delete(this, type, includeSubtypes, depth);
  }

  default IMarker findMarker(long id) throws CoreException {
    for (IMarker m : MarkerStore.find(this, null, true, DEPTH_ZERO))
      if (m.getId() == id)
        return m;
    return null;
  }

  default boolean isTeamPrivateMember() {
    return false;
  }

  default void setTeamPrivateMember(boolean isTeamPrivate) throws CoreException {
  }

  default void setDerived(boolean isDerived, IProgressMonitor monitor) throws CoreException {
  }

  default void setHidden(boolean isHidden) throws CoreException {
  }

  default boolean isPhantom() {
    return false;
  }

  @Override
  default boolean contains(ISchedulingRule rule) {
    if (this == rule)
      return true;
    return rule instanceof IResource && getFullPath().isPrefixOf(((IResource) rule).getFullPath());
  }

  @Override
  default boolean isConflicting(ISchedulingRule rule) {
    if (!(rule instanceof IResource))
      return false;
    IPath a = getFullPath();
    IPath b = ((IResource) rule).getFullPath();
    return a.isPrefixOf(b) || b.isPrefixOf(a);
  }
}
