package com.unitesk.requality.vscode.ui.ide;

import java.io.File;
import java.net.URI;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILightweightLabelDecorator;
import com.unitesk.requality.vscode.ui.resources.IResource;

/**
 * {@code org.eclipse.ui.internal.ide.LinkedResourceDecorator}: adds a link overlay to a linked resource (the warning
 * overlay when its location does not exist).
 */
public class LinkedResourceDecorator implements ILightweightLabelDecorator {
  // VSCODE: PNG overlays of the IDE icons (icons/ide/ovr16), the SVG of Eclipse is not rendered by the compat layer.
  static ImageDescriptor image(String name) {
    return ImageDescriptor.fromResource("icons/ide/ovr16/" + name + ".png");
  }

  @Override
  public void decorate(Object element, IDecoration decoration) {
    if (!(element instanceof IResource))
      return;
    IResource resource = (IResource) element;
    if (resource.isLinked() && !resource.isVirtual()) {
      // VSCODE: linked resources are not supported (isLinked is false), so this is the file-info check of the location.
      URI location = resource.getLocationURI();
      boolean exists = location != null && "file".equals(location.getScheme()) && new File(location).exists();
      decoration.addOverlay(image(exists ? "link_ovr" : "linkwarn_ovr"));
    }
  }
}
