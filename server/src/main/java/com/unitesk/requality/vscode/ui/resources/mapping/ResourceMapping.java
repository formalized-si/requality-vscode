package com.unitesk.requality.vscode.ui.resources.mapping;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.core.resources.mapping.ResourceMapping}: maps a model object to resources. */
public abstract class ResourceMapping implements IAdaptable {

  protected ResourceMapping() {
  }

  /** The model object this mapping represents. */
  public abstract Object getModelObject();

  /** The id of the model provider that created this mapping. */
  public abstract String getModelProviderId();

  /** The projects that contain the resources of this mapping. */
  public abstract IProject[] getProjects();

  /** The resource traversals that cover this mapping. */
  public abstract ResourceTraversal[] getTraversals(ResourceMappingContext context, IProgressMonitor monitor)
      throws CoreException;

  /** Whether this mapping contains the given mapping. */
  public boolean contains(ResourceMapping mapping) {
    return mapping == this;
  }

  @Override
  public Object getAdapter(Class adapter) {
    if (adapter.isInstance(this))
      return this;
    return null;
  }
}
