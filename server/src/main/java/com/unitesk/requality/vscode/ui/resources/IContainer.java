package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.resources.IContainer}. */
public interface IContainer extends IResource {
  int INCLUDE_PHANTOMS = 1;
  int INCLUDE_TEAM_PRIVATE_MEMBERS = 2;
  int INCLUDE_HIDDEN = 8;
  int EXCLUDE_DERIVED = 4;

  IResource[] members() throws CoreException;

  IResource[] members(int memberFlags) throws CoreException;

  IResource findMember(String path);

  IResource findMember(IPath path);

  IResource findMember(String path, boolean includePhantoms);

  boolean exists(IPath path);

  IFile getFile(IPath path);

  IFolder getFolder(IPath path);

  /** Eclipse declares these on IProject and IFolder. */
  IFile getFile(String name);

  IFolder getFolder(String name);

  String getDefaultCharset() throws CoreException;

  /**
   * {@code IContainer.getFilters}: the resource filters of the container. The workspace emulation reads no
   * {@code <filteredResources>} of a {@code .project}, so there are none.
   */
  default IResourceFilterDescription[] getFilters() throws CoreException {
    return new IResourceFilterDescription[0];
  }

  /** {@code IContainer.getDefaultCharset(boolean)}: the charset of the container's files (implicit ones not known here). */
  default String getDefaultCharset(boolean checkImplicit) throws CoreException {
    return getDefaultCharset();
  }

  /** {@code IContainer.setDefaultCharset}: sets the charset of the container's files (null: inherited again). */
  default void setDefaultCharset(String charset, IProgressMonitor monitor) throws CoreException {
  }

  /**
   * {@code IContainer.findDeletedMembersWithHistory}: the files under this container (at the depth) that are deleted and
   * have states in the local history.
   */
  default IFile[] findDeletedMembersWithHistory(int depth, IProgressMonitor monitor) throws CoreException {
    java.util.List<IFile> r = new java.util.ArrayList<>();
    IPath base = getFullPath();
    for (IPath p : FileHistory.pathsWithStates()) {
      if (!base.isPrefixOf(p) || p.segmentCount() == base.segmentCount())
        continue;
      if (depth == DEPTH_ZERO && p.segmentCount() != base.segmentCount() + 1)
        continue;
      IFile f = ResourcesPlugin.getWorkspace().getRoot().getFile(p);
      if (!f.exists())
        r.add(f);
    }
    return r.toArray(new IFile[0]);
  }
}
