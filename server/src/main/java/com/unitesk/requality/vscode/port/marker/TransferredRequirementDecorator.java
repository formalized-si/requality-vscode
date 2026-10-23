package com.unitesk.requality.vscode.port.marker;

import java.util.HashSet;
import java.util.Set;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.nodetype.Requirement;

public class TransferredRequirementDecorator extends RequalityDecorator {

  public TransferredRequirementDecorator() {
    super();
  }

  @Override
  public void addListener(ILabelProviderListener listener) {}

  @Override
  public void dispose() {}

  @Override
  public void decorate(Object element, IDecoration decoration) {
    try {
      //    		RLogger.logInfo("in decorator");
      TreeNode node = (TreeNode) element;
      // TODO
      Attribute attr = node.attrs().getAttribute(Requirement.ATTR_TRANSF_STATUS);
      //    		Requirement.TransferStatus status = Requirement.TransferStatus.
      //    				valueOf(node.getAttribute(Requirement.ATTR_TRANSF_STATUS)
      if (attr != null) {
        //    			RLogger.logInfo("in decorator, attr is not null");
        ImageDescriptor id;
        //    			Requirement.TransferStatus status = Requirement.TransferStatus.valueOf(attr.toString());
        Requirement.TransferStatus status =
            Requirement.TransferStatus.valueOf(attr.getValue().toString());
        if (status.equals(Requirement.TransferStatus.COMPLETE)) {
          id = Activator.getImageDescriptor("icons/overlays/rq_transferred_complete.png");
        } else if (status.equals(Requirement.TransferStatus.INCOMPLETE)) {
          id = Activator.getImageDescriptor("icons/overlays/rq_transferred_incomplete.png");
        } else {
          id = Activator.getImageDescriptor("icons/overlays/rq_transferred_failed.png");
        }

        //    			RLogger.logInfo("id = " + id);

        decoration.addOverlay(id, IDecoration.TOP_RIGHT);
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
    Set<String> set = new HashSet<>();
    set.add(Requirement.ATTR_TRANSF_STATUS);
    return set;
  }
}
