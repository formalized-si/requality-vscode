package com.unitesk.requality.core.operation.mass;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

//TODO what is it?
public class NullTreeNodes extends AbstractNodeOperation {

  private LinkedList<String> nullNodes = null;

  public NullTreeNodes() {
    super("List elements with null references");
    nullNodes = new LinkedList<>();
    // TODO Auto-generated constructor stub
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }

  public void performAction(TreeNode root) {
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(root, true, true, false);
    for (Iterator<TreeNode> i = dftw.iterator(); i.hasNext(); ) {
      TreeNode tn = i.next();
      for (Attribute attr : tn.attrs()) {
        if (attr.getListType() == AttributeType.REFERENCE
            && tn.attrs().getLinkAttributeValue(attr.getKey()) == null) {
          nullNodes.add(tn.getUserVisibleName(true));
        }
      }
    }
  }

  public LinkedList<String> getNullList() {
    return nullNodes;
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
