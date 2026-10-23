package com.unitesk.requality.vscode.ui.ide;

import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILightweightLabelDecorator;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping;
import com.unitesk.requality.vscode.ui.runtime.Adapters;

/**
 * {@code org.eclipse.ui.internal.ide.SymlinkDecorator}: adds the symbolic link overlay to a resource that is a symbolic
 * link.
 */
public class SymlinkDecorator implements ILightweightLabelDecorator {
  @Override
  public void decorate(Object element, IDecoration decoration) {
    if (element instanceof ResourceMapping)
      element = ((ResourceMapping) element).getModelObject();
    IResource resource = Adapters.adapt(element, IResource.class);
    if (resource != null) {
      ResourceAttributes resourceAttributes = resource.getResourceAttributes();
      if (resourceAttributes != null && resourceAttributes.isSymbolicLink())
        decoration.addOverlay(LinkedResourceDecorator.image("symlink_ovr"));
    }
  }
}
