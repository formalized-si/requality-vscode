package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.IPath;

/** {@code org.eclipse.core.resources.IResourceDelta}. */
public interface IResourceDelta {
  int NO_CHANGE = 0;
  int ADDED = 0x1;
  int REMOVED = 0x2;
  int CHANGED = 0x4;
  int ADDED_PHANTOM = 0x8;
  int REMOVED_PHANTOM = 0x10;
  int CONTENT = 0x100;
  int MOVED_FROM = 0x1000;
  int MOVED_TO = 0x2000;
  int OPEN = 0x4000;
  int TYPE = 0x8000;
  int DESCRIPTION = 0x80000;
  int REPLACED = 0x40000;
  int SYNC = 0x10000;
  int MARKERS = 0x20000;
  int ENCODING = 0x200000;

  int getKind();

  int getFlags();

  IResource getResource();

  IPath getFullPath();

  IPath getMovedFromPath();

  IPath getMovedToPath();

  IResourceDelta[] getAffectedChildren();

  IResourceDelta[] getAffectedChildren(int kindMask);

  IResourceDelta[] getAffectedChildren(int kindMask, int memberFlags);

  IResourceDelta findMember(IPath path);
}
