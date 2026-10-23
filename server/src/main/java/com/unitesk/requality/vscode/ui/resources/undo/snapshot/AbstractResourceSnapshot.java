package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.core.internal.resources.undo.snapshot.AbstractResourceSnapshot}: the parent, the attributes and
 * the markers and the local time stamp of a resource. VSCODE: the modification stamp of Eclipse (a counter of the
 * workspace tree) has no counterpart: the file system is the model, whose time stamp is restored.
 */
abstract class AbstractResourceSnapshot<T extends IResource> implements IResourceSnapshot<T> {
  IContainer parent;
  ResourceAttributes resourceAttributes;
  MarkerSnapshot[] markerDescriptions;
  long localTimeStamp = IResource.NULL_STAMP;

  protected AbstractResourceSnapshot() {
  }

  protected AbstractResourceSnapshot(T resource) {
    parent = resource.getParent();
    if (resource.isAccessible()) {
      localTimeStamp = resource.getLocalTimeStamp();
      resourceAttributes = resource.getResourceAttributes();
      List<MarkerSnapshot> markers = new ArrayList<>();
      try {
        for (IMarker marker : resource.findMarkers(null, true, IResource.DEPTH_INFINITE))
          markers.add(new MarkerSnapshot(marker));
      } catch (CoreException e) {
        // the markers are not kept
      }
      markerDescriptions = markers.toArray(new MarkerSnapshot[0]);
    }
  }

  @Override
  public T createResource(IProgressMonitor monitor) throws CoreException {
    T resource = createResourceHandle();
    createExistentResourceFromHandle(monitor);
    restoreResourceAttributes(resource);
    return resource;
  }

  @Override
  public boolean isValid() {
    return parent == null || parent.exists();
  }

  protected void restoreResourceAttributes(IResource resource) throws CoreException {
    if (localTimeStamp != IResource.NULL_STAMP) {
      resource.setLocalTimeStamp(localTimeStamp);
    }
    if (resourceAttributes != null) {
      resource.setResourceAttributes(resourceAttributes);
    }
    if (markerDescriptions != null) {
      for (MarkerSnapshot markerDescription : markerDescriptions) {
        if (markerDescription.resource.exists()) {
          markerDescription.createMarker();
        }
      }
    }
  }

  IWorkspace getWorkspace() {
    return ResourcesPlugin.getWorkspace();
  }

  @Override
  public boolean verifyExistence(boolean checkMembers) {
    IContainer p = parent;
    if (p == null) {
      p = getWorkspace().getRoot();
    }
    return p.findMember(getName()) != null;
  }
}
