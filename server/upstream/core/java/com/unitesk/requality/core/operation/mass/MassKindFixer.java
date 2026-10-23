package com.unitesk.requality.core.operation.mass;

import java.util.ArrayList;
import java.util.Map;
import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;

public class MassKindFixer extends AbstractMassOperation {

  public MassKindFixer() {
    super("MassKindFixer");
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return "Fix 'Вид' attributes";
  }

  @Override
  public boolean execute(TreeNode root) {
    TreeNode rt = root.getTreeDB().getRootNode();
    ArrayList<EnumValue> vals = new ArrayList<>();
    vals.add(new EnumValue("Заголовок", null));
    vals.add(new EnumValue("Требование", null));
    vals.add(new EnumValue("Описание", null));
    vals.add(new EnumValue("Обоснование", null));
    vals.add(new EnumValue("Примечание", null));
    rt.attrs().setConstAttribute("Вид", new EnumDefinitionValue(AttributeType.STRING, vals),0);
    rt.commitAttributes();
    return super.execute(root);
  }

  @Override
  public void performSingleAction(TreeNode node) {
    if (Requirement.TYPE_NAME.equals(node.getType()) || TextNode.TYPE_NAME.equals(node.getType())) {
      try {
        Requirement r = (Requirement) node;
        String newk = "Примечание";
        if (r.getType().equals(Requirement.TYPE_NAME)) {
          if (r.getWorkChildren().size() > 0) {
            newk = "Заголовок";
          } else newk = "Требование";
        } else {
          Attribute a = r.attrs().getAttribute("Вид");
          if (a != null && a.getValue() != null) newk = a.getValue().toString();
        }
        r.attrs()
            .setConstAttribute("Вид", new AppliedEnumValue(newk, null, AttributeType.STRING, "Вид"),0);
        r.commitAttributes();
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
