package com.unitesk.requality.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;

public class Sorting {
  public static List<TreeNode> getSortedWorkChildren( TreeNode node,
      Comparator<TreeNode> c, String type, boolean notcheck, boolean onlyloaded, boolean ignoreCPUP) {
    List<TreeNode> r = node.getWorkChildren(type, notcheck, onlyloaded, ignoreCPUP);
    if (c == null) c = TreeNodeComparator.getComparator(node.getTreeDB());
    r.sort(c);
    //Arrays.sort(r, c);
    return r;
  }
  public static List<Object> formShownChilren(TreeNode node, Comparator<TreeNode> c, boolean notcheck, boolean onlyloaded, boolean sorted) {
    List<Object> result = Collections.emptyList();
    // if(shownChildren!=null &&
    // !getVS().isParentVersionOutdated(Type.ATTRIBUTES))
    // return shownChildren;
    TreeDB db = node.getTreeDB();
    TreeDB dba = db;
    List<Object> uids = new ArrayList<>();
    @SuppressWarnings("unused")
    boolean vchild = false;
    List<Object> childs = ((List<Object>)node.attrs().getListAttributeValue(TreeNode.ATTR_CHILDREN_UUIDS));
    for (Object cid : childs) {
      if (cid == null) continue;
      UUID uid = (UUID) cid;
      if (db.isNodeExist(uid)) {
        String type = db.getNodeType(uid);
        if (type == null && !db.isVirtual())
          for (TreeDB dbalt : Instantiator.getVirtualTreeDBs(db)) {
            db = dbalt;
            type = db.getNodeType(uid);
            if (type != null) break;
          }
        if (type != null && VirtualNode.TYPE_NAME.equals(type)) {
          VirtualNode vn = (VirtualNode) db.getNode(uid);
          if (!vn.isCollapsed()) uids.add(uid);
          else {
            vchild = true;
            uids.addAll(formShownChilren(vn, c, notcheck, onlyloaded, sorted));
            ; // TODO mb shown here?
          }
        } else uids.add(uid);
      }
    }
    if (
    /* (isVirtual()||vchild)&& */ uids.size() > 0 && !node.isVRoot()) {
      TreeDB.updateVisablility(db, uids, notcheck);
    }
//    shownChildren = Collections.unmodifiableList(ch2); 
//    return ch2;
    //int num = sizeShownChildren(onlyloaded);
    for (int i = 0; i < uids.size(); i++) {
      TreeNode ch = null;
      try {
        Object o = uids.get(i);
        if(o instanceof TreeNode)
          ch = (TreeNode)o;
        else
          ch = node.getNodeExc((UUID)o, notcheck, onlyloaded);
      } catch (RuntimeException ex) {
        RLogger.logInfo("Missing node! " + node.getUserVisibleName() + " " + ch +"\n"+CoreUtils.getTrace(ex));
        //ex.printStackTrace();
      }
      if (ch != null) {
        if(result.isEmpty())
          result = new ArrayList<>();
        result.add(ch.getUUId());
      }
    }
    if (c == null && dba!=null) c = TreeNodeComparator.getComparator(dba);
    if (c!=null && !result.isEmpty()) {
      Comparator<TreeNode> fc = c;
      final TreeDB dbf = db;
      if(sorted)
        result.sort(new Comparator<Object>() {
          @Override
          public int compare(Object o1, Object o2) {
            if(o1 instanceof UUID && o2 instanceof UUID) {
              UUID u1 = (UUID) o1;
              UUID u2 = (UUID) o2;
              TreeNode t1 = dbf.getNode(u1);
              TreeNode t2 = dbf.getNode(u2);
              return fc.compare(t1, t2);
            }
            return 0;
          }
        });
    }
    return result;
  }

}
