package com.unitesk.requality.core.nodeiterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.nodetype.VirtualNode;

public class BaseReqChildIterator extends RequalityChildIterator {
  private String lastChistr = null;

  public BaseReqChildIterator(UUID guid, UUID mast, String s) {
    super(guid, mast, RequalityIteratorType.CHILD, s);
    chitype = RequalityChildIteratorType.BASEREQ;
    // TODO Auto-generated constructor stub
  }

  @Override
  public int getCount(VirtualNode master,TreeDB db) {
    return master.getTarget().sizeWorkChildren();
  }

  @Override
  public boolean updateChildren(VirtualNode master,TreeDB db, byte flag) {
    if (master.getTarget() == null) return false;
    VirtualNode im = master;
    // TODO another storages?
    TreeDB vdb = Instantiator.getExistVirtualTreeDB(db, im.getVRoot());
    TreeInstancer ti = vdb.getVStorage();
    vdb.startChangeRow();
    try{
      List<UUID> ids = new ArrayList<>(master.getTarget().getWorkChildrenUUIDs());
      new ArrayList<UUID>();
      List<UUID> oldClones = new ArrayList<>(master.getWorkChildrenUUIDs());
      Map<UUID, UUID> idToUID = new HashMap<>();
      for (UUID uid : oldClones) {
        UUID cp = ti.getCloneParent(uid);
        if (cp != null) {
          // String id = ti.getNodeId(uid);
          // TreeNode targ = master.getTarget();
          // if(id!=null)// && targ.findChild(id)!=null)//else delete?
          idToUID.put(cp, uid);
        }
      }
      boolean res = false;
      StringBuffer chistr = new StringBuffer();
      for (int i = 0; i < getItemsCount(master, db); i++) {
        TreeNode chi = db.getNode(ids.get(i));
        chistr.append(chi.getUUId());
        String newId = chi.getId();
        UUID exId = idToUID.remove(chi.getUUId());
        if (exId != null) {
          // RLogger.logInfo(master.getTarget().getId());
          // TODO update attrs
        } else {
          res = true;
          // if(getInstancerMaster().findChild(newId)!=null)
          // newId =
          // getInstancerMaster().getTarget().getChild(i).getId()+"_"+i;
          UUID uid = UUID.randomUUID();
          Map<String, AttributeDefinition> attrs =
              new HashMap<>(); // AttributeStorage.cloneMap(chi.attrs().getAvailableAttributes(),true,uid);
          //TODO look into situation with _i _I virtual attrs
          //attrs.put("_i", new AttributeDefinition(null, key, vp))
  //        attrs.put("_i", new Attribute(Version.getUnset(), null, "_i", Integer.toString(i)));
  //        attrs.put("_I", new Attribute(Version.getUnset(), null, "_I", Integer.toString(i + 1)));
  //        // TODO new iteration method
          uid = ti.cloneUUID(db, uid, chi, master.getUUId(), newId, null, attrs);
          // ti.getDeltaTarget().created(uid, true);
          // clones.remove(cloneuid);
        }
        // node.attrs().saveAttributes();
      }
      String chistrstr = chistr.toString();
      if (lastChistr == null || !chistrstr.equals(lastChistr)) {
        lastChistr = chistrstr;
      }
      if (idToUID.size() > 0) res = true;
      for (UUID remvd : idToUID.values()) ti.remove(remvd, flag); // TODO check
      //
      return res;
    }
    finally{
      vdb.stopChangeRow();
    }
  }

  @Override
  protected String checkPossibleTarget(VirtualNode master, TreeNode newtarget) {
    //TreeNode master = newtarget.getTreeDB().getNode(getMasterUUID());
    TreeNode par = master.getWorkParent();
    TreeLogic ti = master.getTreeDB().getTreeLogic();
    for (TreeNode node : newtarget.getWorkChildren())
      if (!ti.canBeChildOf(node.getType(), par))
        return "'"
            + par.getUserVisibleName(true)
            + "' cannot be child of '"
            + node.getUserVisibleName(true)
            + "'";
    return null;
  }

  @Override
  public boolean needUpdate(VirtualNode virtualNode) {
    TreeNode targ = virtualNode.getTarget();
    if (targ != null) {
      StringBuffer sb = new StringBuffer();
      TreeDB vdb = Instantiator.getExistVirtualTreeDB(virtualNode.getTreeDB(), virtualNode.getVRoot());
      TreeInstancer ti = vdb.getVStorage();
      for (UUID uid : targ.getWorkChildrenUUIDs()) {
        sb.append(uid);
      }
      if (lastChistr != null && !sb.toString().equals(lastChistr)) { 
        return true;
      }
      if(lastChistr == null) {
        String targetState = sb.toString();
        sb.setLength(0);
        for (UUID uid : virtualNode.getWorkChildrenUUIDs()) {
          UUID cp = ti.getCloneParent(uid);
          sb.append(cp);
        }
        if(!sb.toString().equals(targetState))
          return true;
      }
    }
    return false;
  }
}
