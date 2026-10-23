/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.nodetype;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.VersionsStorage.Type;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.nodeiterator.RequalityChildIterator;
import com.unitesk.requality.core.nodeiterator.RequalityChildIterator.RequalityChildIteratorType;
import com.unitesk.requality.core.nodeiterator.RequalityIterator;
import com.unitesk.requality.core.nodeiterator.RequalityIteratorType;
import com.unitesk.requality.core.nodeiterator.ReuseRequalityChildIterator;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.propagator.VNodePropagator;

public class VirtualNode extends TreeNode {
  public static final String TYPE_NAME = "VirtualNode";
  public static final String ATTR_CHILD_ITERATOR = "_childiterators";
  public static final String ATTR_TARGET = "_target";
  public static final String ATTR_TREECHANGES = "_changes";
  public static final String ATTR_HIDDEN_STATUS = "_%hidden";
  public static final String ATTR_COLLAPSEVNODE = "_hiddenstate";
  public static final String ATTR_TARGET_STRUCT_VERSION = "_%vstversion";

  {
    Attribute.registerStatusName(VirtualNode.ATTR_TARGET);
  }

  //private TreeNode target;
  private boolean moved = false;

  private RequalityChildIterator childiterator;
  private boolean childrenLoaded = false;
  private INodeChangeListener targetChange =
      new INodeChangeListener() {
        @Override
        public void moved(TreeNode source, TreeNode oldParent) {
          try {
            RequalityChildIterator it = getChildIterator();
            if(it!=null) {
              if (it.checkTarget(VirtualNode.this, source) == null) {
              } else {
                setTarget(null);
                commitAttributes();
              }
            }
          } catch (Exception e) {

          }
        }

        @Override
        public void deleted(TreeNode source) {
          if(isDisposed())
            return;
          try {
            setTarget(null);
            commitAttributes();
          } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }

        @Override
        public void attributeChange(TreeNode source, String what, Attribute old) {}

        @Override
        public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
          // TODO Auto-generated method stub

        }

        @Override
        public void fireNodeResourceChange(
            TreeNode treeNode, String resName, Moment m) {
          // TODO Auto-generated method stub

        }
      };
  private boolean onMove = false;

  @Override
  public void setParent(TreeNode parent) {
    TreeNode targ = getTarget();
    try {
      onMove = true;
      RequalityChildIterator it = getChildIterator();
      if(it!=null) { 
        if (it.checkTarget(VirtualNode.this, targ) == null) {
          setTarget(targ);
        } else {
          setTarget(null);
        }
        commitAttributes();
      }
      super.setParent(parent);

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      onMove = false;
    }
  };

  public RequalityChildIterator getChildIterator() {
    //    	if(!isVirtual()){
    //    		TreeNode node = getVirtMirror();
    //    		if(node!=null)
    //    			return ((VirtualNode)node).getChildIterator();
    //    		return null;
    //    	}
    if (childiterator == null) {
      Attribute a = attrs().getAttribute(ATTR_CHILD_ITERATOR);
      if (a != null) {
        String val = (String) a.getValue();
        if (val != null) {
          String[] s = val.split("\\|(?=(?:[^\"]*[\"][^\"]*[\"])*[^\"]*$)");
          if (s.length > 1) {
            childiterator = RequalityChildIterator.fromAttributeString(null, this, val);
          } else
            childiterator =
                (RequalityChildIterator) RequalityIterator.fromAttributeString(getUUId(), val);
        }
      }
    }
    //first initiation
//    if (childiterator == null) {
//      childiterator =
//          (RequalityChildIterator)
//              RequalityChildIterator.getOne(null, getUUId(), RequalityIteratorType.CHILD);
//      if (childiterator.getMasterListener(this) != null)
//        addChangeListener(childiterator.getMasterListener(this));
//    }
    return childiterator;
  }

  public void removeClones() {
    //    	if(!isVirtual()){
    //    		VirtualNode ivn = getVirtMirror();
    //    		if(ivn!=null)
    //    			ivn.removeClones();
    //    		return;
    //    	}
    //
    getChildrenRelatedVirtuals();
    List<UUID> clones = new LinkedList<>(getWorkChildrenUUIDs(true));
    {
      for (UUID uid : clones) {
        try {
          TreeNode nde = getVirtualTreeDB().getNode(uid);
          if(nde!=null)
            nde.delete();
          UUID vuid = Instantiator.revreuses.remove(this);
          if (vuid != null) {
            Instantiator.reuses.get(vuid).remove(this);
            if (Instantiator.reuses.get(vuid).isEmpty()) Instantiator.reuses.remove(vuid);
          }
        } catch (RuntimeException re) {
          re.printStackTrace();
        }
        //getVirtualTreeDB().getNode(uid).fireNodeDelete();
        //getVirtualStorage().remove(uid,true);
      }
    }
    childrenLoaded = false;
    updateLocalAttribute(ATTR_CHILDREN_UUIDS);
    commitAttributes();
  }

  public void setChildIterator(RequalityChildIterator rci) {
    if(rci == null)
      return;
    String childIteratorNow = attrs().getStringAttributeValue(ATTR_CHILD_ITERATOR);
    String toSave = rci.toAttributeString();
    if (childIteratorNow == null || !childIteratorNow.equals(toSave)) {
      attrs().setConstAttribute(ATTR_CHILD_ITERATOR, toSave, Flag.HIDDEN);
    }
  }
  //    private void reloadStorage(){
  //    	initStorageOver(getTarget());
  //    }
  Attribute olda = null;
  @Override
  public boolean typeRelatedAttributeChangeReactionAfter(ChangesSet<Attribute> cs, byte flag) {
    boolean isTarget = cs.contains(ATTR_TARGET),
        isChildIt =
            cs.contains(ATTR_CHILD_ITERATOR);
//    if (s.equals(ATTR_COLLAPSEVNODE)) {
//      try {
//        TreeNode par = getShownParent();
//        getTreeDB().updateNodeChildren(par);
//      } catch (RuntimeException re) {
//        re.printStackTrace();
//        //parent not found
//      }
//    }
    boolean changed = false;
    if (isTarget) { //&&isVirtual()){
      if (isVirtual()) {
        //    			getTreeDB().updateNodeChildren(getCloneParent());
        return false;
      }
      Attribute attribute = cs.getNew(ATTR_TARGET);
      String value =
          (attribute == null || attribute.getValue() == null)
              ? ""
              : attribute.getValue().toString();
      TreeNode oldt = null;
      Attribute oldAttribute = olda;
      if (oldAttribute != null
          && oldAttribute.getValue() != null
          && oldAttribute.getValue().toString().length() > 0)
        try {
          oldt = getTreeDB().getNode(UUID.fromString(oldAttribute.getValue().toString()));
        } catch (Exception e) {
        }
      if (Instantiator.revreuses.containsKey(this)) {
        UUID uid = Instantiator.revreuses.remove(this);
        Instantiator.reuses.get(uid).remove(this);
        if (Instantiator.reuses.get(uid).isEmpty()) Instantiator.reuses.remove(uid);
      }
      TreeNode target = null;
      try {
        target = value.equals("") ? null : getTreeDB().getNode(UUID.fromString(value));
      } catch (Exception e) {
      }
      if (oldt != null && target != null && oldt.getUUId().equals(target.getUUId()))
      {}
      else{
        if (value.equals("") || oldt != null && !oldt.equals(target)) {
          removeClones();
          changed = true;
        }
        childrenLoaded = false;
        if (oldt == null && target != null || oldt != null && !oldt.equals(target)) {
          if (oldt != null) oldt.removeChangeListener(targetChange);
          //updateChildrenIfNeeded();
          UUID ru = Instantiator.revreuses.remove(this);
          if (ru != null) //TODO look into
          Instantiator.reuses.remove(ru);
          if (target != null) {
            target.addChangeListener(targetChange);
            TreeNode targ = getTarget();
            if (targ != null) {
              if (!Instantiator.reuses.containsKey(target.getUUId()))
                Instantiator.reuses.put(target.getUUId(), new HashSet<VirtualNode>());
              Set<VirtualNode> vns = Instantiator.reuses.get(target.getUUId());
              vns.add(this);
              Instantiator.reuses.put(target.getUUId(), vns);
              Instantiator.revreuses.put(this, target.getUUId());
            }
          }
        }
        changed = true;
        olda = attribute;
      }
    }
    if (isChildIt) { //&&isVirtual()){
      if (isVirtual()) {
        //		setChildren(getCloneParent().getWorkChildrenUUIDs());
        return false;
      }
      String old = null;
      if (childiterator != null && childiterator.getMasterListener(this) != null) {
        removeChangeListener(childiterator.getMasterListener(this));
        old = childiterator.toAttributeString();
      }
      childiterator = null;
      
      Attribute attribute = cs.getNew(ATTR_CHILD_ITERATOR);;
      if (old != null
          && (attribute == null || !old.equals(attribute.getValue().toString()))) {
        if (Instantiator.revreuses.containsKey(this)) {
          UUID uid = Instantiator.revreuses.remove(this);
          Instantiator.reuses.get(uid).remove(this);
          if (Instantiator.reuses.get(uid).isEmpty()) Instantiator.reuses.remove(uid);
        }
        changed = true;
        childrenLoaded = false;
      }
      if (attribute != null) {
        Object val = attribute.getValue();
        if (val == null) {
          Attribute a = attrs().getAttribute(ATTR_CHILD_ITERATOR);
          if (a != null) val = a.getValue();
        }
        if(val!=null){
          childiterator = RequalityChildIterator.fromAttributeString(null, this, val.toString());
          childiterator.updateDependencies(this, getTreeDB());
        }
        //updateChildrenIfNeeded();
        if (childiterator != null && childiterator.getMasterListener(this) != null)
          addChangeListener(childiterator.getMasterListener(this));
      }
      if (changed) childrenLoaded = false;
    }
    if (updateChildrenIfNeeded(flag) || changed) {
      //getTreeDB().updateNodeChildren(this);
      //Instantiator.getBaseTree(getTreeDB()).updateNodeChildren(getCloneParent());
      commitAttributes();
      if (isCollapsed()){
        getShownParent().updateLocalAttribute(TreeNode.ATTR_SHOWN_CHILDREN);
        return true;
      }
    }
    return false;
  };
  @Override 
  public boolean updateLocalAttribute(String attr) {
    boolean sup = super.updateLocalAttribute(attr);
    if(attr.equals(ATTR_CHILDREN_UUIDS) || attr.equals(ATTR_COLLAPSEVNODE)) 
      return getWorkParent().updateLocalAttribute(attr);
    return sup;
  }
  @Override
  protected void removeChildUUID(UUID uid, byte flag) {
    super.removeChildUUID(uid, flag);
    if(isCollapsed())
    {
      TreeNode par = getWorkParent();
      par.updateLocalAttribute(ATTR_CHILDREN_UUIDS);
      par.commitAttributes();
    }
  }
  @Override
  protected void addChildUUID(UUID uid, byte flag) {
    super.addChildUUID(uid, flag);
    if(isCollapsed())
    {
      TreeNode par = getWorkParent();
      par.updateLocalAttribute(ATTR_CHILDREN_UUIDS);
      par.commitAttributes();
    }
  }
  
  @Override
  public List<UUID> getWorkChildrenUUIDs(boolean onlyloaded) {
    if (!isDisposed()&&!isVirtual()) {
      if (!onlyloaded) {
      //  updateChildrenIfNeeded();

        //	AttributeStorage.markUp(getTreeDB(), getUUId());
        return getActiveWorkChildrenUUIDs();
      }
    }
    return super.getWorkChildrenUUIDs(onlyloaded);
  }
  //    @Override
  //    public void setChildrenUUIDs(List<UUID> children2,boolean init) {
  //  //  	if(isVirtual()){
  //			super.setChildren(children2,init);
  //	    	if(isCollapsed())
  //				getShownParent().getVS().incVersion(Type.STRUCT);
  //			return;
  ////    	}else
  ////    		if(!init)
  ////    			children2=new ArrayList<UUID>(getVirtualTreeDB().getVStorage().getChildren(getUUId()));
  ////    	Set<UUID> childs = new HashSet<UUID>(children2);
  ////    	//int cnt = childs.size();
  ////    	//childs.addAll(getVNChildren(true));
  ////    	super.setChildrenUUIDs(new ArrayList<UUID>(childs),init);
  ////    	//if(isVHidden()){
  ////    		//getTreeDB().updateNodeChildren(getParent(true));
  ////    	//}
  ////    	if(isCollapsed())
  ////			getTreeDB().updateShownNodeChildren(getShownParent(),init);
  //	}
  @Override
  public void clearShownChildren() {
    if(isDisposed())
      return;
    super.clearShownChildren();
    if(isCollapsed()){
      getShownParent().clearShownChildren();
    }
    //getTreeDB().updateNodeChildren(getShownParent());
    //}
  }

  public void setTarget(TreeNode node) throws Exception {
    //getVirtMirror();
    if (node != null && (getQualifiedId() + "/").contains(node.getQualifiedId() + "/"))
      throw new RuntimeException("Recursion on targeting mechanism is prohibited");
    //if(target != null)
    //target.removeChangeListener(targetChange);
    boolean changes = true;
    String targ = attrs().getStringAttributeValue(ATTR_TARGET);
    if (targ != null)
      changes = node == null || !targ.equals(node.getSUUId());
    else
      changes = node != null;
    //if(target!=null)
    //target.addChangeListener(targetChange);
    if (changes) {
      String s = attrs().getStringAttributeValue(ATTR_TARGET);
      if (node == null || !node.getSUUId().equals(s)) {
        attrs().setConstAttribute(ATTR_TARGET, node != null ? node.getSUUId() : "",
            Flag.HIDDEN);
        //	reloadStorage();
      }
    }
  }

  public TreeNode getTarget() {
    String tid = attrs().getStringAttributeValue(ATTR_TARGET);
    if(tid == null || tid.isEmpty())
      return null;
    TreeNode target = null;
    try {
      target = getTreeDB().getNode(tid);
      if (target != null) target.addChangeListener(targetChange);
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return target;
  }

  public int getCloneCount() {
    if (getTarget() == null) return 0;
    return getClones().size();
  }

  public boolean hide;

  public TreeDB getVirtualTreeDB() {
    TreeDB db = getTreeDB();
    if (db.isVirtual()) return db;
    String uid = getVRoot();
    if (uid != null) return Instantiator.getExistVirtualTreeDB(db, uid);
    return db;
    //Instantiator.getVirtualTreeDB(getTreeDB(),Instantiator.DEFAULT_INSTANCE_NAME);
  }

  public VirtualNode() {
    this(null, null);
  }

  private boolean updateChildrenIfNeeded(byte flag) {
    return updateChildrenIfNeeded(false, flag);
  }

  public static void setHidden(TreeNode node, boolean state) {
    Attribute hid = node.attrs().getAttribute(VirtualNode.ATTR_HIDDEN_STATUS);

    if (state) {
      if (hid == null) ; //what?;
      //changedAttribute.put(uid, new HashMap<String, Attribute>());
      node.attrs().setConstAttribute(VirtualNode.ATTR_HIDDEN_STATUS, true,
          Flag.HIDDEN);
    } else {
      if (hid != null) {
        node.attrs().removeAttribute(VirtualNode.ATTR_HIDDEN_STATUS);
      }
    }
  }

  @Override
  public TreeNode findWorkChild(String id) {
    for (UUID clone : getWorkChildrenUUIDs()) {
      TreeNode node = getVirtualTreeDB().getNode(clone);
      if (node.getId().equals(id) || id.equals(node.attrs().getStringAttributeValue(TreeInstancer.ATTR_CLONE_ID)))
        return node;
    }
    return super.findWorkChild(id);
  }

  @Override
  public List<TreeNode> findWorkChildPlusName(String id) {
    Set<TreeNode> nodes = new HashSet<>();
    for (UUID clone : getWorkChildrenUUIDs()) {
      TreeNode node = getVirtualTreeDB().getNode(clone);
      if (node.getId().equals(id)
          || id.equals(node.getName())
          || id.equals(node.attrs().getStringAttributeValue(TreeInstancer.ATTR_CLONE_ID))) nodes.add(node);
    }
    nodes.addAll(super.findWorkChildPlusName(id));
    return new ArrayList<>(nodes);
  };

  public boolean updateChildrenIfNeeded(boolean force, byte flag) {
    if(!nodeLoaded)
      return false;
    if(isDisposed())
      return false;
    //    	if(isVirtual()){
    //    		VirtualNode ivn = (VirtualNode) getCloneParent();
    //    		if(ivn!=null)
    //    			return ivn.updateChildrenIfNeeded(force);
    //    		return false;
    //    	}
    //if(getCloneParent().isVirtual())
    //	return false;//TODO more proper virt reuse
    if (onMove) return false;
    if (moved) {
      RequalityChildIterator it = getChildIterator();
      if (it!=null && it.checkTarget(VirtualNode.this, getTarget()) != null)
        try {
          setTarget(null);
          return true;
        } catch (Exception e) {
          e.printStackTrace();
        }
      moved = false;
    }
    boolean res = false;
    getChildrenRelatedVirtuals();
    getChildIterator();
    if (!childrenLoaded || force) {
      boolean ciup = childiterator!=null && childiterator.needUpdate(this),
          nup = ciup || childiterator == null;
      if(ciup){
        getChildIterator().needUpdate(this);
        childiterator = getChildIterator();
        childrenLoaded = true;
      }
      TreeNode targ = getTarget();
      if(nup){
        if (Instantiator.revreuses.containsKey(this)) {
          UUID uid = Instantiator.revreuses.remove(this);
          Instantiator.reuses.get(uid).remove(this);
          if (Instantiator.reuses.get(uid).isEmpty()) Instantiator.reuses.remove(uid);
        }
        if (targ != null) {
          if (!Instantiator.reuses.containsKey(targ.getUUId()))
            Instantiator.reuses.put(targ.getUUId(), new HashSet<VirtualNode>());
          Set<VirtualNode> vns = Instantiator.reuses.get(targ.getUUId());
          vns.add(this);
          Instantiator.reuses.put(targ.getUUId(), vns);
          Instantiator.revreuses.put(this, targ.getUUId());
        }
      }
      if (ciup) {//Change rows may cause dead locks
        //getTreeDB().startChangeRow();
        //getTreeDB().startChangeRow();
        try{
          res = getChildIterator().updateChildren(VirtualNode.this, getTreeDB(), flag);
          if (getTarget() == null)
            attrs().removeAttribute(ATTR_TARGET_STRUCT_VERSION);
          else
            attrs()
                .setConstAttribute(
                    ATTR_TARGET_STRUCT_VERSION,
                    getTarget().getVS().getVersion(Type.STRUCT).toString(),Flag.HIDDEN|Flag.VIRTUAL);
          if(res){
            updateLocalAttribute(TreeNode.ATTR_CHILDREN_UUIDS);
            //DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(this,false,false,false,false,false);
//            Iterator<TreeNode> it = dftw.iterator();
//            while(it.hasNext())
//              it.next();
          }
        }
        finally{
          //getTreeDB().stopChangeRow();
        }
      }
      //getTreeDB().fireNodeChange(this, true);
      //getTreeDB().fireNodeGUIChange(this, true);
      //fireGUINodeAttributeChange(TreeNode.ATTR_UNDEFINED);
      //getTreeDB().stopChangeRow();
    }
    return res;
  }
  //    @Override
  //    public TreeNode getChild(int i) {
  //		updateChildrenIfNeeded();
  //		//int cnt = getRawChildrenUUID().size();
  //
  //		return getVirtualTreeDB().getNode(uid);
  //    };
  @Override
  public List<UUID> getWorkChildrenUUIDs() {
    //    	if(!isVirtual()){
    //    		TreeNode ivn = getVirtMirror();
    //    		if(ivn!=null)
    //    			return ivn.getWorkChildrenUUIDs();
    //    		return new ArrayList<UUID>();
    //    	}
    //if(!raw)//&&super.getRawChildrenUUIDs(true).size()>0)
    //    	if(isVirtual() && getVS().isCloneParentVersionOutdated(Type.STRUCT) ||
    //    			!isVirtual() && updateChildrenIfNeeded())
    //    		getTreeDB().updateNodeChildren(this);
    //List<UUID> clones = getVNChildren(true);
    //if(clones!=null)
    //			clones = new ArrayList<UUID>(clones);
    //	else
    //	clones = new ArrayList<UUID>();
    //clones.addAll(super.getRawChildrenUUIDs());
    return super.getWorkChildrenUUIDs();
    //return clns;
    //return getVNChildren(raw);
  }

  public VirtualNode(TreeDB db, UUID uuid) {
    super(db, uuid);
    addAPropogator(VNodePropagator.getOne());
  }

  public void saveChildIterator() {
    setChildIterator(getChildIterator());
  }

  public boolean isCollapsed() {
    boolean hiddenLayer;
    Object value = attrs().getAttributeValue(ATTR_COLLAPSEVNODE);
    if (value instanceof Boolean) hiddenLayer = (Boolean) value;
    else if (value instanceof String) hiddenLayer = ((String) value).toLowerCase().equals("true");
    else hiddenLayer = false;
    return hiddenLayer;
  }

  public List<UUID> getClones() {
    return getWorkChildrenUUIDs();
  }
  //	public List<UUID> getIterChildren(boolean raw) {
  ////		if(!isVirtual()){
  ////    		VirtualNode ivn = getVirtMirror();
  ////    		if(ivn!=null)
  ////    			return ivn.getIterChildren(raw);
  ////    		return new ArrayList<UUID>();
  ////    	}
  //
  //		List<UUID> childs = getVirtualTreeDB().getVStorage().getRepChildren(getUUId(),raw),//TODO make it iter
  //				   tosee = new ArrayList<UUID>(childs);
  //		TreeDB db = getVirtualTreeDB();
  //		if(!raw)
  //			for(UUID uid:tosee){
  //				TreeNode node = db.getNode(uid);
  //				String s = node.attrs().evaluatePredicateString((db.getVStorage()).getPredicate(uid));
  //				if(s.equals(""))
  //					continue;
  //				if(s.startsWith("0")||s.equals("false"))
  //					childs.remove(uid);
  //			}
  //		return childs;
  //	}
  //	public List<UUID> getVNChildren(boolean raw) {
  //		TreeInstancer ti = getVirtualTreeDB().getVStorage();
  //		List<UUID> childs =  ti.getRepChildren(getUUId(),raw),
  //				   tosee = new ArrayList<UUID>(childs);
  //		TreeDB db = getVirtualTreeDB();
  //		if(!raw)
  //			for(UUID uid:tosee){
  //				TreeNode node = db.getNode(uid);
  //				String s = node.attrs().evaluatePredicateString(ti.getPredicate(uid));
  //				if(s.equals(""))
  //					continue;
  //				if(s.startsWith("0")||s.equals("false"))
  //					childs.remove(uid);
  //			}
  //    	return childs;
  //	}
  public static void updateClonesChildren(TreeNode treeNode) {
    if (treeNode == null) return;
    TreeDB db = treeNode.getTreeDB();
    if (!db.isVirtual()) {
      String uid = treeNode.getVRoot();
      if (uid != null) db = Instantiator.getExistVirtualTreeDB(treeNode.getTreeDB(), uid);
      else {
        db = treeNode.getTreeDB();
        if(db.isVirtual())
          db = Instantiator.getRootTree(db);
        //db = Instantiator.getExistVirtualTreeDB(treeNode.getTreeDB(),Instantiator.DEFAULT_INSTANCE_NAME,null,null);
      }
    }
    //TODO many instances
//    if (db != null) {
//      db.startChangeRow();
//      Collection<UUID> clones = Instantiator.getClonesAndVnodes(treeNode.getUUId(), db);
//      //TODO needed?
//      //	if(!treeNode.isVirtual())
//      //	db.getNode(treeNode.getUUId()).setChildrenUUIDs(treeNode.getRawChildrenUUIDs());
//      for (UUID uid : clones) {
//        TreeNode clone = db.getCachedNodeOrVirt(uid);
//        if (clone != null) {
//          //TODO struct eventws
////          clone.fireGUINodeAttributeChange(TreeNode.formIdEvent());
////          db.fireNodeGUIChange(clone, TreeNode.IdEvent, Collections.EMPTY_MAP, true);
//        }
//      }
//      db.stopChangeRow();
//    }
  }

  @Override
  public boolean canEditAttributesInTable() {
    return true;
  }

  public void collapse() {
    attrs().setConstAttribute(ATTR_COLLAPSEVNODE, true,Flag.HIDDEN);
  }

  public void unCollapse() {
    attrs().removeAttribute(ATTR_COLLAPSEVNODE);
  }
//
//  @Override
//  public void getUserVisibleName(boolean inner, boolean fc, StringBuilder sb) {
//    if (isCollapsed()) getShownParent().getUserVisibleName(inner, fc, sb);
//    else super.getUserVisibleName(inner, fc, sb);
//  }

  public void markMoved() {
    moved = true;
  }

  public static boolean toBeDeleted(TreeNode node, TreeNode workParent) {
    if(workParent!=null && workParent.getType().equals(VirtualNode.TYPE_NAME)){
      VirtualNode vn = (VirtualNode)workParent;
      try {
        if (vn.isCollapsed()) return true;
//        else {
//          setTarget(null);
//          commitAttributes();
//        }
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return false;
  }

  public boolean isBase() {
    RequalityChildIterator chi = getChildIterator();
    return chi!=null && chi.getChildIteratorType()==RequalityChildIteratorType.BASEREQ;
  }
  @Override
  protected void fireLoaded(){
    super.fireLoaded();
    getChildIterator();
  }
  @Override
  protected void fireCreated(byte flag){
    if(childiterator == null){
      TreeNode targ = getTarget();
      childiterator =
          RequalityChildIterator.getOne(null, this, RequalityIteratorType.CHILD, ReuseRequalityChildIterator.EMPTY);
      if (childiterator.getMasterListener(this) != null)
        addChangeListener(childiterator.getMasterListener(this));
    }else{
      updateChildrenIfNeeded(flag);
      applyLocalAttributes(flag, false);//TODO is it needed?
    }
  }

  public static void updateChildrenOfClone(TreeNode node) {
    try {
      UUID cpu = node.getCloneParentUUId();
      if (!node.isDisposed()&& cpu != null) {
        TreeNode cp = node.getCloneParent();
        if (cp == null){
          return;
        }
        //getCloneParent().getWorkChildrenUUIDs(false); // up cp children
        if (node.getVS().isCloneParentVersionOutdated(Type.STRUCT)) {
          // getVS().setCloneParentVersion(Type.STRUCT,
          // getCloneParent().getVS().getVersion(Type.STRUCT),true);
          UUID cloneParent = cpu;
          {
            boolean changes = false;
            Collection<UUID> result = new HashSet<>();
            Collection<UUID> stchi = new HashSet<>();
            node.getChildrenRelatedVirtuals();
            Collection<Object> stored = node.attrs().getListAttributeValue(TreeNode.ATTR_CHILDREN_UUIDS); // getRepChildren(uuid,raw);
            if (stored != null) {
              for(Object o:stored) {
                UUID uid = (UUID) o;
                result.add(uid);
                stchi.add(uid);
              }
            }
            boolean w = false;
            boolean rl = false;
            if (cpu.equals(node.getUUId())) {
              // workChildren = new ArrayList<UUID>(res);
            } else
              try {
                rl = cp.writeWCLock();
                w = node.writeWCLock();
                List<UUID> childs = cp.getWorkChildrenUUIDs();
                {
                  Set<UUID> curCloneParent = new HashSet<>();
                  if (childs != null)
                    curCloneParent.addAll(childs);
                  Set<UUID> toAdd = new HashSet<>(curCloneParent);
                  Iterator<UUID> resit = result.iterator();
                  TreeDB tree = node.getTreeDB();
                  if(tree.isVirtual())
                    tree = Instantiator.getRootTree(tree);
                  while (resit.hasNext()) {
                    UUID uid = resit.next();
                    UUID cpa = node.getVirtualDB().getVStorage().getCloneParent(uid);
                    if (!curCloneParent.contains(cpa)
                        || !tree.isNodeExist(cpa)) {
                      //remove clones of already removed nodes
                      TreeNode nodea = tree.getNode(uid);
                      resit.remove();
                      if (nodea != null) nodea.delete();
                    } else toAdd.remove(cpa);
                    // else it is new added one
                    // TODO changes of type or attrs?
                  }
                  node.getVS().updateCloneParentVersion(Type.STRUCT);
                  for (UUID uid : toAdd) {
                    if (uid.equals(node.getUUId())) {
                      RLogger.logInfo("WARN! rec prevented " + node.getQualifiedId(true));
                      continue;
                    }
                    try {
                      TreeNode base = node.getTreeDB().getNode(uid);
                      if (base == null) continue;
                      UUID uid2 = UUID.randomUUID();
                      UUID cid =
                          node.getVirtualDB()
                              .getVStorage()
                              .cloneUUID(node.getTreeDB(), uid2, base, node.getUUId(), base.getId(), stchi, null);
                      if (cid == null) RLogger.logInfo("cloning error! " + uid2);
                      else {
                        result.add(cid);
                        stchi.add(cid);
                      }
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                  }
                  node.getChildrenRelatedVirtuals();
                  node.setChildrenUUIDs(new CopyOnWriteArrayList<>(result), true);//TODO remove, excess
                  if (!toAdd.isEmpty() || childs == null || childs.size() != result.size()) 
                    changes = true;
                  // if(changes){

                  // }
                  //getVS().updateCloneParentVersion(Type.STRUCT);
                }
              } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
              finally{
                if(w)
                  node.writeWCUnlock();
                if(rl)
                  cp.writeWCUnlock();
              }
              if (changes) {
                node.getVS().updateVersion(Type.STRUCT);
                node.updateLocalAttribute(ATTR_CHILDREN_UUIDS);
                node.commitAttributes();
              }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
