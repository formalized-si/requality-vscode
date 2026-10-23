package com.unitesk.requality.vscode.ui.resources.mapping;

import com.unitesk.requality.vscode.ui.resources.IResource;

/** {@code org.eclipse.core.resources.mapping.ResourceTraversal}: resources to visit with a depth and flags. */
public class ResourceTraversal {
  private final IResource[] resources;
  private final int depth;
  private final int flags;

  public ResourceTraversal(IResource[] resources, int depth, int flags) {
    this.resources = resources;
    this.depth = depth;
    this.flags = flags;
  }

  public IResource[] getResources() {
    return resources;
  }

  public int getDepth() {
    return depth;
  }

  public int getFlags() {
    return flags;
  }
}
