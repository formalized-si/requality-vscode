package com.unitesk.requality.vscode.port.marker;

import java.util.HashSet;
import java.util.Set;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.Activator;

public abstract class TreeNodeStatusDecorator extends RequalityDecorator {

  @Override 
  public Set<String> getRelatedKeys() {
    Set<String> set = new HashSet<>();
    set.add(getAttributeName());
    return set;
  }
  
  protected abstract String getStatusPrefix();

  protected abstract String getAttributeName();

  public TreeNodeStatusDecorator() {
    super();
  }

  @Override
  public void addListener(ILabelProviderListener listener) {}

  @Override
  public void dispose() {}

  private void addOverlay(IDecoration decoration, String name, int pos) {
    ImageDescriptor id = Activator.getImageDescriptor("icons/overlays/" + name);
    decoration.addOverlay(id, pos);
  }

  @Override
  public void decorate(Object element, IDecoration decoration) {
    String pref = getStatusPrefix();
    TreeNode node = (TreeNode) element;
    Attribute attr = node.attrs().getAttribute(getAttributeName());
    if (attr != null) {
      addOverlay(
          decoration,
          pref + (Attribute.valueToString(null, attr.getType(),attr.getValue())) + ".png",
          IDecoration.TOP_LEFT);
    } else {
      addOverlay(decoration, pref + "in process.png", IDecoration.TOP_LEFT);
    }
  }

  @Override
  public void removeListener(ILabelProviderListener listener) {}

  @Override
  public boolean isLabelProperty(Object element, String property) {
    return false;
  }
}
