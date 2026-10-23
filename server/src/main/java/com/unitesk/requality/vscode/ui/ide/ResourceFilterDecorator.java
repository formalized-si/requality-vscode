package com.unitesk.requality.vscode.ui.ide;

import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILightweightLabelDecorator;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResourceFilterDescription;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.ui.internal.ide.ResourceFilterDecorator}: adds the filter overlay to a container with filters. */
public class ResourceFilterDecorator implements ILightweightLabelDecorator {
  @Override
  public void decorate(Object element, IDecoration decoration) {
    if (!(element instanceof IContainer))
      return;
    try {
      IResourceFilterDescription[] filters = ((IContainer) element).getFilters();
      if (filters.length > 0)
        decoration.addOverlay(LinkedResourceDecorator.image("filterapplied_ovr"), IDecoration.BOTTOM_RIGHT);
    } catch (CoreException e) {
      // ignored, as Eclipse does
    }
  }
}
