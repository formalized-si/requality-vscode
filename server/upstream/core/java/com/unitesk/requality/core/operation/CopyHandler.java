package com.unitesk.requality.core.operation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public class CopyHandler {

  public Object execute(List<TreeNode> nodes) {
    copyNodes(nodes, false);
    return null;
  }

  public void copyNodes(Collection<TreeNode> nodes, boolean cutNode) {
    if (nodes == null || nodes.size() == 0) return;
    TreeDB db = nodes.iterator().next().getTreeDB();
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    for (TreeNode tn : nodes) {
      if (!(tn.isVirtual()?Instantiator.getRootTree(tn.getTreeDB()):tn.getTreeDB()).equals(db) || !db.getTreeLogic().isCopyable(tn))
        return;
    }
    List<TreeNode> workList = new ArrayList<>();
    SortedMap<UUID, String> quids = new TreeMap<>();
    for (TreeNode node : nodes) quids.put(node.getUUId(), node.getQualifiedId());
    for (TreeNode node : nodes) {
      boolean add = true;
      for (TreeNode n2 : nodes)
        if (n2 != node && quids.get(node.getUUId()).startsWith(quids.get(n2.getUUId()) + "/")) {
          add = false;
          break;
        }
      if (add) workList.add(node);
    }
    CopyPasteHelper.getInstance().setNodes(workList);
  }

  public boolean isEnabled(Collection<TreeNode> nodes) {
    if (nodes == null || nodes.size() < 1) {
      return false;
    }
    TreeDB db = nodes.iterator().next().getTreeDB();
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    for (TreeNode tn : nodes) {
      TreeDB adb = tn.getTreeDB();
      if(adb.isVirtual())
        adb = Instantiator.getRootTree(adb);
      if (!adb.equals(db) || !db.getTreeLogic().isCopyable(tn))
        return false;
    }
    return true;
  }
}
