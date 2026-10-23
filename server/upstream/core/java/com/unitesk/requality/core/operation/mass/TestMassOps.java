package com.unitesk.requality.core.operation.mass;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;

public class TestMassOps extends AbstractMassOperation {

  public TestMassOps() {
    super("TEst op");
    // TODO Auto-generated constructor stub
  }

  @Override
  public void performSingleAction(TreeNode root) {
    /*
     * Attribute a = root.getAttribute(TreeNode.ATTR_DESCRIPTION); if (a !=
     * null){ String str = (String)a.getValue(); Attribute b =
     * root.getAttribute("coverageStatus"); if(b != null){
     * if(b.getResultType() == AttributeType.STRING){ String str2 = (String)
     * b.getValue(); root.putAttribute(new Attribute(root,
     * AttributeType.STRING, TreeNode.ATTR_DESCRIPTION, str2 + '.' + str));
     * root.sa } }
     */
    Attribute b = root.attrs().getAttribute(TreeNode.ATTR_DESCRIPTION);
    if (b != null) {
      if (!b.getType().equals(null)) {
        String str = (String) b.getValue();
        Pattern p = Pattern.compile("(\\w+)\\.(.*)", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher m = p.matcher(str);
        if (m.find()) {
          root.attrs().setConstAttribute("coverageStatus", m.group(1),0);
          root.setDescription(m.group(2));
          //root.attrs().setAttribute(TreeNode.ATTR_DESCRIPTION, m.group(2));
          root.commitAttributes();
        }
      }
    }
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
