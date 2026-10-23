package com.unitesk.requality.vscode.ui.ide;

import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILightweightLabelDecorator;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IResource;

/** {@code org.eclipse.ui.internal.ide.VirtualResourceDecorator}: adds the virtual overlay to a virtual folder. */
public class VirtualResourceDecorator implements ILightweightLabelDecorator {
  @Override
  public void decorate(Object element, IDecoration decoration) {
    if (element instanceof IFolder && ((IResource) element).isVirtual())
      decoration.addOverlay(LinkedResourceDecorator.image("virt_ovr"), IDecoration.BOTTOM_RIGHT);
  }
}
