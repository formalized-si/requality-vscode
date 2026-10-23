package com.unitesk.requality.core.operation.mass;

import java.util.Map;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;

public class MassReqTypeFixer extends AbstractMassOperation {

  public MassReqTypeFixer() {
    super("MassReqTypeFixer");
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return "Fix textnodes with req children";
  }

  @Override
  public void performSingleAction(TreeNode node) {
    if (TextNode.TYPE_NAME.equals(node.getType())) {
      try {
        TreeNode doc = node;
        boolean change = false;
        for (TreeNode chi : doc.getWorkChildren())
          if (chi.getType().equals(Requirement.TYPE_NAME)) change = true;
        if (change) {
          while (!doc.getType().equals(Requirement.TYPE_NAME)) {
            doc.setType(Requirement.TYPE_NAME);
            String vid = "Примечание";
            if (doc.attrs().containsKey("Вид"))
              vid = doc.attrs().getAttributeValue("Вид").toString();
            doc.attrs().setConstAttribute("Тип объекта", vid,0);
            doc.commitAttributes();
            doc = doc.getWorkParent();
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
