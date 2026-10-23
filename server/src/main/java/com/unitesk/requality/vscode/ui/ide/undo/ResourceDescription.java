package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.ui.ide.undo.ResourceDescription}: the description of a resource, kept for the undo. Replaced by the
 * snapshots of {@code org.eclipse.core.resources.undo.snapshot} (see {@link ResourceSnapshotFactory}).
 */
@Deprecated
public abstract class ResourceDescription {
  /** The description of a resource (its snapshot). */
  @Deprecated
  public static ResourceDescription fromResource(IResource resource) {
    IResourceSnapshot<IResource> delegate = ResourceSnapshotFactory.fromResource(resource);
    return new ResourceDescription() {
      @Override
      public IResource createResourceHandle() {
        return delegate.createResourceHandle();
      }

      @Override
      public String getName() {
        return delegate.getName();
      }

      @Override
      public IResource createResource(IProgressMonitor monitor) throws CoreException {
        return delegate.createResource(monitor);
      }

      @Override
      public void createExistentResourceFromHandle(IResource unusedResource, IProgressMonitor monitor)
          throws CoreException {
        delegate.createExistentResourceFromHandle(monitor);
      }

      @Override
      public boolean isValid() {
        return delegate.isValid();
      }

      @Override
      public void recordStateFromHistory(IResource unusedResource, IProgressMonitor monitor) throws CoreException {
        delegate.recordStateFromHistory(monitor);
      }

      @Override
      public boolean verifyExistence(boolean checkMembers) {
        return delegate.verifyExistence(checkMembers);
      }
    };
  }

  public abstract IResource createResourceHandle();

  public abstract String getName();

  public abstract IResource createResource(IProgressMonitor monitor) throws CoreException;

  public abstract void createExistentResourceFromHandle(IResource resource, IProgressMonitor monitor)
      throws CoreException;

  public abstract boolean isValid();

  public abstract void recordStateFromHistory(IResource resource, IProgressMonitor monitor) throws CoreException;

  public abstract boolean verifyExistence(boolean checkMembers);
}
