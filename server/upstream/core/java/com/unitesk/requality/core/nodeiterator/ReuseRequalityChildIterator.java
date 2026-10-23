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
package com.unitesk.requality.core.nodeiterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.attribute.VersionsStorage.Type;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.nodetype.VirtualNode;

public class ReuseRequalityChildIterator extends RequalityChildIterator {
  private static final String ATTR_LAST_GEN_PARAM = "_%lastGenAttr";
  public static final String EMPTY = "0|";
  // private String idPredicate = "$i";
  protected Map<UUID, Map<String, INodeChangeListener>> idAttributeListeners =
      new HashMap<>();
  /***
   * Contains set of attributes names on which iteration is performing. May contain duplicates 
   */
  private List<String> listAttrs = new ArrayList<>();

  @Override
  public INodeChangeListener getMasterListener(VirtualNode master) {
    if(listener == null)
      return null;
    listener = new INodeChangeListener() {
      @Override
      public void moved(TreeNode source, TreeNode oldParent) {}

      @Override
      public void deleted(TreeNode source) {}

      @Override
      public void attributeChange(TreeNode source, final String what,Attribute old) {
        attributesChange(
            source,
            new ChangesSet<>(Collections.emptySet(),AttributeStorage.formSet(what),Collections.emptySet(),
                AttributeStorage.formAttributeMap(old),
                AttributeStorage.formAttributeMap(source.attrs().getAttribute(what)),
                AttributeStorage.formList(what)));
      }

      Set<String> changes = new HashSet<>();

      @Override
      public void attributesChange(TreeNode source, ChangesSet<Attribute> wh) {
        changes.clear();
        for (String s : wh.getAll())//TODO rewrite
            if (listAttrs.contains(s)) changes.add(s);
        if (!changes.isEmpty()) {
          master.removeChangeListener(listener);
          for (String what : changes)
            if (listAttrs.contains(what)) {
              if (source.attrs().getAttribute(what) == null
                  || !source
                      .attrs()
                      .getAttribute(what)
                      .getTValue().getType()
                      .equals(AttributeType.LIST)) {
                while (listAttrs.contains(what)) listAttrs.remove(what);
              }
              master.removeClones();
            }
          ((VirtualNode) source).saveChildIterator();
          ((VirtualNode) source).commitAttributes();
          master.addChangeListener(listener);
        }
      }

      @Override
      public void fireNodeResourceChange(
          TreeNode treeNode, String resName, Moment m) {
        // TODO Auto-generated method stub

      }
    };
    return listener;
  }

  private INodeChangeListener listener = null;

  public ReuseRequalityChildIterator(UUID guid, UUID mast, String s) {
    super(guid, mast, RequalityIteratorType.CHILD, s);

    chitype = RequalityChildIteratorType.REUSE_CARTESIAN;
    String[] del = s.split("\\|");

    int cnt = Integer.parseInt(safeGet(del, 1, "0"));
    for (int i = 0; i < cnt; i++) {
      String var = safeGet(del, i + 2);
      if (var.startsWith("\"")) var = var.substring(1, var.length() - 1);
      listAttrs.add(var);
    }
  }

  private String safeGet(String[] del, int s) {
    return safeGet(del, s, "");
  }

  // prefer to use it. for some cases del will be empty
  private String safeGet(String[] del, int s, String def) {
    if (s >= del.length || del[s].equals("")) return def;
    return del[s];
  }

  @Override
  public String toString() {
    // TODO change
    return "type: " + type + " target: ";
  }

  @Override
  public boolean equals(Object obj) {
    if (super.equals(obj)) return true;
    if (!(obj instanceof ReuseRequalityChildIterator)) {
      return false;
    }
    return super.equals(obj);
  }

  public String toAttributeString() {
    String s =
        super.toAttributeString() + "|" + getChildIteratorType().ordinal() + "|" + listAttrs.size();
    for (int i = 0; i < listAttrs.size(); i++) s += "|\"" + listAttrs.get(i) + "\"";
    return s;
  }

  //
  // public String getNodeId(int i, TreeNode node){
  // return idGenerator.getValue(i, node).toString();
  //// updateDependencies(node);
  // // return node.evaluatePredicateString(node.getUUId(),
  // // RequalityAttributeGenerator.replaceRegexpVar(idPredicate, "$i",
  // i+""));
  // }
  //
  public int getCount(VirtualNode master, TreeDB db) {
    switch (getChildIteratorType()) {
      case REUSE_CARTESIAN:
        {
          int cnt = 1;
          for (String val : listAttrs) {
            Attribute a = master.attrs().getAttribute(val);
            if (a != null) {
              {
                if (a.getTValue().getType().equals(AttributeType.LIST)) {
                  cnt *= ((ListValue) a.getTValue().getGValue()).getValues().size();
                }
              }
            }
          }
          return cnt - ignoredIds.size();
        }

      default:
        break;
    }
    return 0;
  }

  public List<String> getAttributeVars() {
    return listAttrs;
  }

  public void setAttributeVar(VirtualNode master, TreeDB db, int num, String value) {
    if (num < 0 || num >= listAttrs.size()) return;
    if (!listAttrs.get(num).equals(value)
        && master.attrs().getAttribute(value) != null) {
      master.removeClones();
      listAttrs.set(num, value);
      master.saveChildIterator();
    }
  }

  public List<String> getAttributeNames(VirtualNode master, TreeDB db) {
    List<String> nm = new ArrayList<>();
    for (Attribute a : master.attrs())
      if (a.getTValue().getType().equals(AttributeType.LIST) && !a.getKey().startsWith("_"))
        nm.add(a.getKey());
    return nm;
  }

  public void addAttributeVar(VirtualNode master, TreeDB db) {
    master.removeClones();
    // Set<String> keys =
    // getInstancerMaster().getAvailableGenerators(false).keySet();
    // if(!keys.isEmpty())
    // key = keys.iterator().next();
    listAttrs.add(getAttributeNames(master, db).get(0));
    master.saveChildIterator();
  }

  public void removeAttributeVar(VirtualNode master, TreeDB db, int num) {
    if (num < 0 || num >= listAttrs.size()) return;
    master.removeClones();
    listAttrs.remove(num);
    master.saveChildIterator();
    master.commitAttributes();
  }

  public String getAttributeVar(int i) {
    if (i < 0 || i >= listAttrs.size()) return "";
    return listAttrs.get(i);
  }

  private List<Integer> ignoredIds = new ArrayList<>();
  private Map<String, Attribute> lastUpVers = null;

  @Override
  public boolean needUpdate(VirtualNode virtualNode) {
    TreeNode target = virtualNode.getTarget(); 
    if (target == null) return false;
    Version stored = virtualNode
        .getVS()
        .getVersion(VirtualNode.ATTR_TARGET_STRUCT_VERSION);
    List<TreeNode> nds = virtualNode.getWorkChildren();
    if(stored == null || stored.isUnset()) {      
      stored = target.getVS().getVersion(Type.STRUCT);
      for(TreeNode nd: nds)
        if(!nd.getCloneParentUUId().equals(target.getUUId()))
          stored = null;
      
    }
    if (stored!=null && !stored.equals(virtualNode.getTarget().getVS().getVersion(Type.STRUCT))) {
      return true; // struct changes
    }
    if(getCount(virtualNode, target.getTreeDB())!=nds.size())
      return true;
    if(lastUpVers!=null)
      for (String s : listAttrs) {
        Attribute a = virtualNode.attrs().getAttribute(s);
        if (a != null
            && a
                != (lastUpVers.containsKey(s) ? lastUpVers.get(s) : null)) {
          return true; // attr changes//TODO fix it
        }
      }
    return false;
  }

  public boolean updateChildren(VirtualNode master, TreeDB db, byte flag) {
    if (master.getTarget() == null) 
      return false;
    master.attrs().getStringAttributeValue(ATTR_LAST_GEN_PARAM, "");
    String cuset = toFullAttributeString(master, db);
    // if(lastStore.equals(cuset))
    // /return;//TODO update needed?
    VirtualNode virtualNode = master;
    if(lastUpVers == null)
      lastUpVers = new HashMap<>();
    for (String s : listAttrs) {
      Attribute a = virtualNode.attrs().getAttribute(s);
      if (a != null) lastUpVers.put(s, a);
      else lastUpVers.remove(s);
    }
    // TODO another storages
    TreeDB vdb = db;
    if (!db.isVirtual()) {
      vdb = Instantiator.getExistVirtualTreeDB(db, master.getVRoot());
    }
    TreeInstancer ti = vdb.getVStorage();
    vdb.startChangeRow();
    try{
      int count = getItemsCount(master, db);
      int[] cnt = new int[listAttrs.size()];
      int[] max = new int[listAttrs.size()];
      for (int i = 0; i < max.length; i++)
        if (!listAttrs.get(i).equals("")) {
  
          Object rawValue = master.attrs().getAttributeValue(listAttrs.get(i));
          if (rawValue instanceof ListValue) max[i] = ((ListValue) rawValue).getValues().size();
          else if (rawValue != null) max[i] = 1;
        }
      ignoredIds = new ArrayList<>();
      TreeNode targ = master.getTarget();
      if (targ != null){
        virtualNode.attrs().setAttribute(new AttributeDefinition(virtualNode.getUUId(),
            VirtualNode.ATTR_TARGET_STRUCT_VERSION, 
            new ConstValueProvider(AttributeType.STRING,
                new Version(targ.getVS().getVersion(Type.STRUCT).getInt()).toString())));
      }
      List<UUID> curChilds = new ArrayList<>(master.getWorkChildrenUUIDs());
      List<UUID> toDel = new LinkedList<>();
      Iterator<UUID> chii = curChilds.iterator();
      //while (chii.hasNext()) if (ti.getCloneParent(chii.next()) == null) chii.remove();
      Map<String, UUID> idToUID = new HashMap<>();
      for (UUID uid : curChilds) {
        UUID cp = ti.getCloneParent(uid);
        if (cp != null && cp.equals(master.getTarget().getUUId())) {
          String val = (String) db.getAttributeValue(cp, TreeInstancer.ATTR_CLONE_ID, null, true);
//          if(o){
//            Attribute a = node.attrs().getAttribute(TreeInstancer.ATTR_CLONE_ID);
//            if(a!=null)
//              val = a.getValue().toString();
//          }else{
//            //to avoid treenode loading
//            Map<String, AttributeDefinition> atrs = db.getStorage().getAttributes(uid); 
//            if (atrs != null && atrs.get(TreeInstancer.ATTR_CLONE_ID) != null)
//              val = atrs.get(TreeInstancer.ATTR_CLONE_ID).getRawValue().getValue().toString();
//          }
          if(val!=null)
            idToUID.put(val, uid);
          else
            toDel.add(uid);
        }else
            toDel.add(uid);
      }
      boolean ret = false;
      for (int i = 0; i < count; i++) {
        StringBuffer newId = new StringBuffer();
        newId.append(master.getTarget().getId());
  
        UUID uid = UUID.randomUUID();
  
        Map<String, AttributeDefinition> attrs =
            new HashMap<>(); // AttributeStorage.cloneMap(tattrs,true,uid);
        //TODO look BaseReqChildIterator
  //      attrs.put("_i", new Attribute(Version.getUnset(), null, "_i", Integer.toString(i)));
  //      attrs.put("_I", new Attribute(Version.getUnset(), null, "_I", Integer.toString(i + 1)));
  
        Map<String, Integer> names = new HashMap<>();
        for (int j = 0; j < max.length; j++)
          if (!listAttrs.get(j).equals("")) {
            String key = listAttrs.get(j);
            List<Object> vals = new ArrayList<>();
            Attribute basicList = master.attrs().getAttribute(key);
            AttributeType valsType = AttributeType.STRING;
            if (basicList != null) {
              if (basicList.getType().equals(AttributeType.LIST)) {
                valsType = basicList.getListType();
                vals = ((ListValue) basicList.getValue()).getValues();
              } else {
                valsType = basicList.getType();
                vals.add(basicList.getValue());
              }
            }
            if (vals != null && !vals.isEmpty()) {
              String attrName = key;
              if (!names.containsKey(key)) names.put(key, 0);
              names.put(key, names.get(key) + 1);
              if (names.get(key) > 1) attrName += "_" + names.get(key);
              // list = new LinkedList<Attribute>();
              AttributeDefinition ad = new AttributeDefinition(uid, 
                  attrName, 
                  AttributeAvailability.SUBTREE,
                  AttributeDefinition.detectValueProvider(
                    TreeNode.ATTR_NAME, valsType, vals.get(cnt[j]),
                    TempFolderFilesProvider.getNewNotInited(valsType)), 
                  0
              ); 
  //            final Attribute a =
  //                new Attribute(Version.getUnset(), null, valsType, attrName, vals.get(cnt[j]));
  //            a.setAvailability(AttributeAvailability.SUBTREE);
              // list.add(a);
              attrs.put(attrName, ad);
              newId.append(attrName);
              newId.append("-");
              newId.append(Attribute.valueToString(null, valsType, vals.get(cnt[j])));
              // attrs.put(attrName, new
              // ArrayList<Attribute>(){{{add(a);}}});
              // ag =
              // node.addAttributeIterator(UUID.randomUUID(),node,ParameterAvailability.SUBTREE.ordinal()+"|"+key+"|"+AttributeValueIteratorType.FORMULA_ID+"|\""+vals.get(cnt[j]).toString()+"\"");
              // node.saveAttributeIterators();
            } // TODO else?
          }
        String resID = newId.toString();
        UUID exId = idToUID.remove(resID);
        if (exId != null) {
           RLogger.logInfo(resID+" possibly needs update");
          // TODO update attrs
        } else {
          ret = true;
          uid =
              ti.cloneUUID(
                  db,
                  uid,
                  master.getTarget(),
                  master.getUUId(),
                  resID,
                  null,
                  attrs);
          ;
          // clones.remove(cloneuid);
        }
        if (cnt.length > 0) cnt[0]++;
        int j = 0;
        while (j < cnt.length && cnt[j] >= max[j]) {
          cnt[j] = 0;
          if (j + 1 < cnt.length) cnt[j + 1]++;
          j++;
        }
        // node.attrs().saveAttributes();
      }
      if (idToUID.size() > 0) ret = true;
      for (UUID id : toDel){
        ti.remove(id, flag);
      }
      for (UUID remvd : idToUID.values()) 
        ti.remove(remvd, flag); // TODO check
      master.attrs().setAttribute(new AttributeDefinition(master.getUUId(), 
          ATTR_LAST_GEN_PARAM, new ConstValueProvider(AttributeType.STRING, cuset)));
  
      //master.commitAttributes();
      // for(UUID uid:clones)
      // master.getVirtualStorage().remove(uid);
      // ti.saveChanges();
      return ret;
    }
    finally{
      vdb.stopChangeRow();
    }
  }

  @SuppressWarnings("unchecked")
  private String toFullAttributeString(VirtualNode master, TreeDB db) {
    StringBuffer sb = new StringBuffer();
    sb.append(master);
    sb.append("|");
    sb.append(listAttrs.size());
    sb.append("|");
    for (int i = 0; i < listAttrs.size(); i++) {
      sb.append(listAttrs.get(i));
      sb.append("|");
      if (!listAttrs.get(i).equals("")) {
        Object rawValue = master.attrs().getAttributeValue(listAttrs.get(i));
        if (rawValue instanceof List) sb.append(((List<Object>) rawValue).size());
        else if (rawValue != null) sb.append(1);
        else {
          Attribute a = master.attrs().getAttribute(listAttrs.get(i));
          if (a != null) {
            AttributeGenerator gen = a.getDefinition().getGenerator();
            if (gen != null) sb.append(gen.getItemsCount());
          }
        }
      }
      sb.append("|");
    }
    return sb.toString();
  }

  public List<Integer> getIgnored() {
    return ignoredIds;
  }

  @Override
  protected String checkPossibleTarget(VirtualNode master, TreeNode newtarget) {
    TreeNode par = master.getShownParent();
    if(par == null)
      return "Cannot drop '"
      + newtarget.getUserVisibleName(true)
      + "' to root project element";
    TreeLogic ti = newtarget.getTreeDB().getTreeLogic();
    if (par!=null && !ti.canBeChildOf(newtarget.getType(), par))
      return "'"
          + newtarget.getUserVisibleName(true)
          + "' cannot be child of '"
          + par.getUserVisibleName(true)
          + "'";
    return null;
  }

  @Override
  public void updateDependencies(VirtualNode master, TreeDB db) {
    for (String s : listAttrs) {
      Attribute at = master.attrs().getAttribute(s);
      if (at != null) {
        TreeNode origin = master.getTreeDB().getNode(at.getOrigin());
        Set<String> deps = dependencies.get(origin);
        if (deps == null) deps = new HashSet<>();
        dependencies.put(origin, deps);
      }
    }
  }
}
