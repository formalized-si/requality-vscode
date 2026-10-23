package com.unitesk.requality.core.operation.mass;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;

public class MassRefFixer extends AbstractMassOperation {

  public MassRefFixer() {
    super("MassKindFixer");
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return "Fix References attributes";
  }

  @Override
  public boolean execute(TreeNode root) {
//    Map<UUID, String> ids = ReqIFModel.loadMapping();
//    for (UUID uid : ids.keySet()) {
//      TreeNode node = root.getTreeDB().getNode(uid);
//      if (node != null) {
//        node.attrs().setAttribute("ForeignID", ids.get(uid));
//        node.saveAttributes();
//      } else RLogger.logInfo("Node " + uid + " is not found for" + ids.get(uid) + "!");
//    }
//    return true;
     toup.clear();
     boolean b =
     super.execute(root.getTreeDB().getNode(Requirement.getTypeRootQId()));
     for(TreeNode node:toup){
       RelManager.getRevertLinks(node, true, null);
       node.commitAttributes();
     }
     return b;
  }

  Set<TreeNode> toup = new HashSet<>();

  @Override
  public void performSingleAction(TreeNode node) {
    TreeNode rt = node;
    List<Pair<String, TreeNode>> refs = rt.getRelations(null);
    if (refs != null) {
      Map<String, List<Object>> ref = new HashMap<>();

      for (Pair<String, TreeNode> link : refs) {
        if (!ref.containsKey(link.getFirst())) ref.put(link.getFirst(), new LinkedList<>());
        toup.add(link.getSecond());
        ref.get(link.getFirst()).add(link.getSecond().getSUUId());
      }
      for (String s : ref.keySet()) {
        Attribute a = node.attrs().getAttribute(s);
        if (a != null && a.getOrigin().equals(node.getUUId())) {
          if (ref.get(s).size() == 1)
//            node.attrs()
//                .setAttribute(
              node.attrs().setAttribute(new AttributeDefinition(a.getOrigin(), a.getKey(),a.getAvailability(),
                  new ReferenceValueProvider(AttributeType.REFERENCE, ref.get(s).iterator().next().toString()),a.getDefinition().getFlags()));//TODO check
//                    AttributeType.REFERENCE, s, ref.get(s).iterator().next().toString());
          else
            node.attrs().setAttribute(new AttributeDefinition(a.getOrigin(), a.getKey(),a.getAvailability(),
                new ReferenceValueProvider(AttributeType.LIST, new ListValue(AttributeType.REFERENCE, ref.get(s))),a.getDefinition().getFlags()));//TODO check
            //node.attrs().setAttribute(s, new ListValue(AttributeType.REFERENCE, ref.get(s)));
        }
      }
      rt.commitAttributes();
      for (Pair<String, TreeNode> link : refs) {
        toup.add(link.getSecond());
      }
    }
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
