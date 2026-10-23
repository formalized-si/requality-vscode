package com.unitesk.requality.vscode.ui.resources;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.runtime.IPath;

/** {@code org.eclipse.core.internal.events.ResourceDelta}: the change of a resource and of its affected children. */
final class ResourceDelta implements IResourceDelta {
  private final IResource resource;
  private final int kind;
  private final int flags;
  private final IPath movedTo;
  private final List<ResourceDelta> children = new ArrayList<>();

  ResourceDelta(IResource resource, int kind, int flags, IPath movedTo) {
    this.resource = resource;
    this.kind = kind;
    this.flags = flags;
    this.movedTo = movedTo;
  }

  void add(ResourceDelta child) {
    children.add(child);
  }

  @Override
  public int getKind() {
    return kind;
  }

  @Override
  public int getFlags() {
    return flags;
  }

  @Override
  public IResource getResource() {
    return resource;
  }

  @Override
  public IPath getFullPath() {
    return resource.getFullPath();
  }

  @Override
  public IPath getMovedFromPath() {
    return (flags & MOVED_FROM) != 0 ? resource.getFullPath() : null;
  }

  @Override
  public IPath getMovedToPath() {
    return movedTo;
  }

  @Override
  public IResourceDelta[] getAffectedChildren() {
    return children.toArray(new IResourceDelta[0]);
  }

  @Override
  public IResourceDelta[] getAffectedChildren(int kindMask) {
    return getAffectedChildren(kindMask, 0);
  }

  @Override
  public IResourceDelta[] getAffectedChildren(int kindMask, int memberFlags) {
    List<IResourceDelta> r = new ArrayList<>();
    for (ResourceDelta c : children)
      if ((c.kind & kindMask) != 0 && (c.flags & memberFlags) == memberFlags)
        r.add(c);
    return r.toArray(new IResourceDelta[0]);
  }

  @Override
  public IResourceDelta findMember(IPath path) {
    if (path.equals(getFullPath()))
      return this;
    for (ResourceDelta c : children) {
      IResourceDelta found = c.findMember(path);
      if (found != null)
        return found;
    }
    return null;
  }
}
