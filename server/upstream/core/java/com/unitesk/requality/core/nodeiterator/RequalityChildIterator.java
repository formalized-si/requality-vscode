package com.unitesk.requality.core.nodeiterator;

import java.util.Iterator;
import java.util.UUID;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public abstract class RequalityChildIterator extends RequalityIterator {
  protected RequalityChildIteratorType chitype = null;

  public enum RequalityChildIteratorType {
    REUSE_CARTESIAN,
    BASEREQ
  };

  public String toAttributeString() {
    return super.toAttributeString() + "|" + Integer.toString(chitype.ordinal());
  }

  public static RequalityChildIterator fromAttributeString(UUID uid, TreeNode it, String s) {
    RequalityIteratorType nit = RequalityIteratorType.ATTRIBUTE;
    if (s != null && !s.equals("")) {
      String p = "";
      if (s.indexOf("|") > -1) {
        p = s.substring(0, s.indexOf("|"));
        s = s.substring(s.indexOf("|") + 1);
        if (!s.isEmpty() && s.startsWith("UUID:")) {
          String ts = s.substring(5, s.length());
          int t = ts.indexOf("|");
          int end = t != -1 ? t : s.length();
          if (end > 0) uid = UUID.fromString(ts.substring(0, end - 1));
          if (t > -1) s = ts.substring(end + 1);
        }
      } else p = s;
      if (p.equals("")) p = "0";
      nit = RequalityIteratorType.values()[Integer.parseInt(p)];
    }
    try {
      return getOne(uid, it, nit, s);
    } catch (Exception e) {
    }
    return null;
  }

  public RequalityChildIterator(UUID master, UUID guid, RequalityIteratorType tp, String s) {
    super(guid, tp);
    String tp2 = "0";
    if (s.indexOf("|") > 0) {
      tp2 = s.substring(0, s.indexOf("|"));
      chitype = RequalityChildIteratorType.values()[Integer.parseInt(tp2)];
    } else {
      tp2 = s;
      if (tp2.equals("")) tp2 = "0";
    }
  }

  public RequalityChildIteratorType getChildIteratorType() {
    if (chitype == null) return RequalityChildIteratorType.REUSE_CARTESIAN;
    return chitype;
  }

  public int getItemsCount(VirtualNode master, TreeDB db) {
    if (master.getTarget() == null) return 0;
    return getCount(master, db);
  }

  public abstract int getCount(VirtualNode master, TreeDB db);

  public abstract boolean updateChildren(VirtualNode master, TreeDB db, byte flag);

  public static RequalityChildIterator getOne(
      UUID guid, TreeNode it, RequalityIteratorType nit, String s) {
    String cl = null;
    if (s.indexOf("|") > -1) cl = s.substring(0, s.indexOf("|"));
    else cl = s;
    if (cl == null || cl.equals("")) cl = "0";
    Integer i = Integer.parseInt(cl);
    if (s.indexOf("|") > -1) s = s.substring(s.indexOf("|") + 1);
    else s = "";
    switch (RequalityChildIteratorType.values()[i]) {
      case BASEREQ:
        {
          return new BaseReqChildIterator(guid, it.getUUId(), s);
        }
      case REUSE_CARTESIAN:
        {
          return new ReuseRequalityChildIterator(guid, it.getUUId(), s);
        }
    }
    return null;
  }

  protected abstract String checkPossibleTarget(VirtualNode master, TreeNode newtarget);

  public String checkTarget(VirtualNode master, TreeNode newtarget) {
    if (newtarget == null) return "Target is not set";
    TreeNode mnode = master;
    if ((mnode.getQualifiedId() + "/").startsWith(newtarget.getQualifiedId() + "/"))
      return "Target on '" + newtarget.getUserVisibleName(true) + "' causes recursion";
    if ((newtarget.getQualifiedId() + "/").startsWith(mnode.getQualifiedId() + "/"))
      return "Cannot select elements being already reused by altering virtual node";
    String s = checkPossibleTarget(master, newtarget);
    if (s != null) return s;
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(newtarget, true, true, false);
    Iterator<TreeNode> it = dftw.iterator();
    while (it.hasNext()) {
      TreeNode nod = it.next();
      if (nod.getType().equals(VirtualNode.TYPE_NAME)) {
        TreeNode targ = ((VirtualNode) nod).getTarget();
        if (targ != null && mnode.equals(targ))
          return "Target on '" + newtarget.getUserVisibleName(true) + "' causes recursion";
      }
    }
    return null;
  }

  public INodeChangeListener getMasterListener(VirtualNode virtualNode) {
    return null;
  }

  public void updateDependencies(VirtualNode master, TreeDB db) {}

  public boolean needUpdate(VirtualNode virtualNode) {
    return false;
  }
}
