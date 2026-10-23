package com.unitesk.requality.vscode.ui.resources.mapping;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.core.resources.mapping.ModelProvider}: a model that maps its objects to resources. The default
 * implementations return no mappings, as in Eclipse for resources without a model object of this provider.
 */
public abstract class ModelProvider implements IAdaptable {

  protected ModelProvider() {
  }

  /** The mappings of this model provider for the resource (none by default). */
  public ResourceMapping[] getMappings(IResource resource, ResourceMappingContext context, IProgressMonitor monitor)
      throws CoreException {
    return new ResourceMapping[0];
  }

  /** The mappings of this model provider for the resources (none by default). */
  public ResourceMapping[] getMappings(IResource[] resources, ResourceMappingContext context,
      IProgressMonitor monitor) throws CoreException {
    return new ResourceMapping[0];
  }

  /** The traversals of the mappings: the traversals of each mapping, concatenated. */
  public ResourceTraversal[] getTraversals(ResourceMapping[] mappings, ResourceMappingContext context,
      IProgressMonitor monitor) throws CoreException {
    java.util.List<ResourceTraversal> result = new java.util.ArrayList<>();
    for (ResourceMapping mapping : mappings)
      for (ResourceTraversal t : mapping.getTraversals(context, monitor))
        result.add(t);
    return result.toArray(new ResourceTraversal[0]);
  }

  @Override
  public Object getAdapter(Class adapter) {
    return null;
  }
}
