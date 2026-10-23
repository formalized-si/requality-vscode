package com.unitesk.requality.marker;

import java.util.HashSet;
import java.util.Set;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.Activator;

public class VirtualityDecorator extends RequalityDecorator {

  public VirtualityDecorator() {
    super();
  }

  @Override
  public void addListener(ILabelProviderListener listener) {}

  @Override
  public void dispose() {}

  private void addOverlay(IDecoration decoration, int pos) {
    ImageDescriptor id = Activator.getImageDescriptor("icons/overlays/virtual.gif");
    decoration.addOverlay(id, pos);
  }

  @Override
  public void decorate(Object element, IDecoration decoration) {
    try {
      TreeNode node = (TreeNode) element;
      if (node.isVirtual()) {
        addOverlay(decoration, IDecoration.BOTTOM_LEFT);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void removeListener(ILabelProviderListener listener) {}

  @Override
  public boolean isLabelProperty(Object element, String property) {
    return false;
  }

  @Override
  public Set<String> getRelatedKeys() {
    Set<String> keys = new HashSet<>();
    keys.add(TreeNode.ATTR_VIRT_STATUS );
    return keys;
  }
}
