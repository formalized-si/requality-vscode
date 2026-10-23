package com.unitesk.requality.core.operation.mass;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class MassIDRenames extends AbstractNodeOperation {

  public MassIDRenames() {
    super("");
  }

  @Override
  public String getName() { // first transaction name
    return "Change numerical ids to temporary";
  }

  Pattern p = Pattern.compile("[0-9]*[_]?[0-9]*");

  @Override
  public void performAction(TreeNode root) {
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(root, true, true, true);
    Map<UUID, String> newIds = new HashMap<>();
    for (TreeNode node : dftw) {
      List<TreeNode> sch = node.getWorkChildren();
      Set<String> ids = new HashSet<>();
      for (TreeNode sn:sch) {
        String id = sn.getId();
        if (p.matcher(id).matches()) {
          String tempid = sn.getId() + "_temp";
          int cnt = 0;
          while (node.findWorkChild(tempid + cnt) != null) cnt++;
          newIds.put(sn.getUUId(), RequalityCLI.getNextId(ids));
          ids.add(newIds.get(sn.getUUId()));
          sn.setId(tempid + cnt);
          sn.commitAttributes();
        } // cannot be non-num id breaking all order?
      }
    }
    root.getTreeDB().commit();
    //TODO error on false or multi-level transes
    root.getTreeDB().startTransaction("Change numerical ids to new generated"); // second
    // transaction
    // name
    for (TreeNode node : dftw) {
      if (newIds.containsKey(node.getUUId())){
        node.setId(newIds.get(node.getUUId()));
        node.commitAttributes();
      }
    }
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
