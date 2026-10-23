package com.unitesk.requality.vscode.ui.resources.mapping;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdapterFactory;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.core.internal.resources.mapping.ResourceMappingAdapterFactory}: a resource (file, folder or project)
 * adapts to the {@link ResourceMapping} of the resource itself, so the popupMenus contributions for resource mappings
 * (Team, Compare With, Replace With) apply to the resources of the explorer as they do to the model nodes.
 */
public class ResourceMappingAdapterFactory implements IAdapterFactory {
  /** {@code org.eclipse.core.resources.ResourceMapping.getModelProviderId} of the resources. */
  public static final String MODEL_PROVIDER_ID = "org.eclipse.core.resources.modelProvider";

  @Override
  public Object getAdapter(Object adaptableObject, Class adapterType) {
    if (adapterType == ResourceMapping.class && adaptableObject instanceof IResource)
      return new ResourceElementMapping((IResource) adaptableObject);
    return null;
  }

  @Override
  public Class[] getAdapterList() {
    return new Class[] { ResourceMapping.class };
  }

  /** The mapping of one resource: its traversal is the resource itself and its children. */
  static final class ResourceElementMapping extends ResourceMapping {
    private final IResource resource;

    ResourceElementMapping(IResource resource) {
      this.resource = resource;
    }

    @Override
    public Object getModelObject() {
      return resource;
    }

    @Override
    public String getModelProviderId() {
      return MODEL_PROVIDER_ID;
    }

    @Override
    public IProject[] getProjects() {
      return new IProject[] { resource.getProject() };
    }

    @Override
    public ResourceTraversal[] getTraversals(ResourceMappingContext context, IProgressMonitor monitor) throws CoreException {
      return new ResourceTraversal[] { new ResourceTraversal(new IResource[] { resource }, IResource.DEPTH_INFINITE, 0) };
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof ResourceElementMapping && ((ResourceElementMapping) o).resource.equals(resource);
    }

    @Override
    public int hashCode() {
      return resource.hashCode();
    }
  }
}
