package com.unitesk.requality.vscode.ui.ltk.actions;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/**
 * {@code org.eclipse.ltk.internal.ui.refactoring.actions.AbstractResourcesHandler}: the resources of the selection.
 */
public abstract class AbstractResourcesHandler extends AbstractHandler {

  /** The resources of the structured selection (the elements that are resources or adapt to one). */
  protected IResource[] getSelectedResources(IStructuredSelection sel) {
    List<IResource> resources = new ArrayList<>(sel.size());
    for (Object next : sel) {
      if (next instanceof IResource) {
        resources.add((IResource) next);
      } else if (next instanceof IAdaptable) {
        IResource resource = ((IAdaptable) next).getAdapter(IResource.class);
        if (resource != null)
          resources.add(resource);
      }
    }
    return resources.toArray(new IResource[0]);
  }
}
