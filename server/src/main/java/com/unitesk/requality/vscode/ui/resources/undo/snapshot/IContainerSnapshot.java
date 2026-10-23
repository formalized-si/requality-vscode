package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;

/** {@code org.eclipse.core.resources.undo.snapshot.IContainerSnapshot}: the snapshot of a folder, project or workspace path. */
public interface IContainerSnapshot<T extends IContainer> extends IResourceSnapshot<T> {
  IResourceSnapshot<? extends IResource>[] getMembers();

  void addMember(IResourceSnapshot<? extends IResource> member);
}
