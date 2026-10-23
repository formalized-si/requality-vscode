package com.unitesk.requality.core.attribute;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import com.unitesk.requality.core.attribute.reference.IReferenceVersionRule;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.LinkResolver;

public class ReferenceValue implements IContextStringProvider, IStringParamConsumer, IStringParamProvider{
  private static final String SEPARATOR = "||";
  public static final String CP_SEPARATOR = "::";
  public static final String VERSION_PREFIX = "version-";
  private String raw;
  private boolean isError = false;
  private String key = null, stringValue = null;
  private UUID[] nodes, oldNodes = null;
  private Map<UUID, String>trees;
  private Map<UUID, String> vers = null, resVers = null;
  private boolean versInited = false;
  public boolean markVersOutdated = false;
  private CopyOnWriteArrayList<IMark> warns;
  private boolean rpFirst = true;
  private IReferenceVersionRule versRule;
  private ReferenceValueProvider rp;
  private AttributeStorage as;
  private ReferenceValue(AttributeStorage as,Object o, String key, IReferenceVersionRule versRule) {
    if(o instanceof ReferenceValue)
      throw new RuntimeException("Ref value where it does not supposed to be");
    this.as = as;
    this.raw = o.toString();
    this.key = key;
    if(versRule!=null){
      this.versRule = versRule;
      vers = new TreeMap<>();
    }
  }
  private ReferenceValue(Object o, String key, UUID[]oldNodes, Map<UUID,String> vers, IReferenceVersionRule versRule) {
    if(o instanceof ReferenceValue)
      throw new RuntimeException("Ref value where it does not supposed to be");
    this.raw = o.toString();
    this.key = key;
    this.versRule = versRule;
    if(vers!=null){
      versInited = true;
      this.vers = vers;
      this.oldNodes = oldNodes;
    }else{
      if(versRule!=null){
        this.versRule = versRule;
        vers = new TreeMap<>();
      }
    }
  }
  public ReferenceValue(String key, String error) {
    this.raw = "";
    this.key = key;
    stringValue = error;
    isError = true;
  }
  public ReferenceValue(String key, Map<TreeNode, String> set, AttributeStorage where, IReferenceVersionRule versRule) {
    this.raw = "";
    this.key = key;
    stringValue = "resolved term";//TODO mb rewrite
    nodes = new UUID[set.size()];
    this.versRule = versRule;
    if(vers!=null && resVers == null)
      resVers = new HashMap<>();
    int i = 0;
    vers = new HashMap<>();
    for(TreeNode node:set.keySet()){
      vers.put(node.getUUId(), set.get(node));
      if(!node.isSameTree(where.getNode())){
        if(trees == null)
          trees = new HashMap<>();
        trees.put(node.getUUId(), node.getTreeDB().getProjectName());
      }
      nodes[i++] = node.getUUId();
      if(resVers!=null)
        resVers.put(node.getUUId(), node.getNodeVersion());
    }
  }
  public UUID[] getNodeUUIDs(AttributeStorage where) {
    if(stringValue == null)
      resolve(where, true);
    return nodes;
  }
  public TreeNode[] getNodes(AttributeStorage where) {
    return getNodes(where, true);
  }
  public TreeNode[] getNodes(AttributeStorage where, boolean persist) {
    UUID[]nds = nodes;
    if(stringValue == null || !persist)
      nds = resolve(where, persist);
    if(nds == null)
      return null;
    TreeNode[]anodes = new TreeNode[nds.length];
    int i = 0;
    for(UUID uid:nds){
      TreeDB db = where.getNode().getTreeDB();
      if(trees!=null){
        String aname = trees.get(uid);
        if(aname!=null)
          db = db.getTracker().getTreeDB(db, null, aname, false, db.getMonitor());
      }
      if(db!=null){
        TreeNode node = db.getNode(uid);
        if(node!=null)
          anodes[i++] = node;
        else{
          stringValue = null;
          nds = resolve(where, persist);
	        if(nds == null)
	          continue;
        }
      }
    }
    if(i<anodes.length)
      return Arrays.copyOf(anodes, i);
    return anodes;
  }
  public UUID[] resolve(AttributeStorage where,boolean persist){
    try{
      isError = false;
      if(rpFirst && getStoredNodeVers() == null)
        vers = new HashMap<>();
      LinkResolver lr = where.getNode().getTreeDB().getLinkResolver();
      Object val = raw;
      if(val == null){
        if(persist){
          stringValue = ReferenceValueProvider.NULL_MESSAGE;
          isError = true;
        }
      }else{
        if(val instanceof String && ((String)val).startsWith(ReferenceValueProvider.ERROR_CHAR)){
          if(persist){
            stringValue = val.toString();
            isError = true;
          }
        }else{
          List<TreeNode> lvars = Collections.emptyList();
          try {
            lvars = lr.findNodesByPathIgnoreDisposed(where.getNode(), key, where.getNode().getTreeDB(), raw.toString(), LinkResolver.RESOLVE_MODE);
          } catch (NodeNotFoundException e) {
          }
          if(lvars.size() == 0){
            if(persist){
              stringValue = ReferenceValueProvider.MISSING_MESSAGE.replace("{val}", raw.toString()).replace("{key}", key);
              isError = true;
            }
            return null;
          }
          UUID[]ret = null;
          if(lvars.size() == 1)
          {
            TreeNode node = lvars.iterator().next();
            node.waitLoaded();
            ret = new UUID[]{node.getUUId()};
            String versS = null;
            String old = null;
            if(persist){
              nodes = ret;
              if(vers!=null && resVers == null)
                resVers = new HashMap<>();
              if(resVers!=null)
                resVers.put(node.getUUId(), node.getNodeVersion());
              IReferenceVersionRule rvr = 
                  where.getNode().getTreeDB().getAttributeRules().getReferenceVersionRule();
              if(rvr!=null && rvr.checkFrom(where.getNode(), key) && getStoredNodeVers() != null && !versInited)
              {
                getStoredNodeVers().put(node.getUUId(), node.getNodeVersion());
                this.oldNodes = nodes;
                this.versInited = true;
              }
              if(getStoredNodeVers()!=null){
                if(versInited)
                  old = getStoredNodeVers().get(node.getUUId());
                versS = node.getNodeVersion();
                if(versS != null && (old != null && !versS.equals(old))){
                  markVersOutdated = true;
                  addWarn(new ReferenceOutdateMark(where.getNode().getTreeDB().getProjectName(),where.getNode().getUUId(), node.getTreeDB().getProjectName(), node.getUUId(),
                      where.getNode().getUserVisibleName(),
                      node.getUserVisibleName(),key, old, versS));
                }//else
                  //getStoredNodeVers().put(node.getUUId(),versS);
              }
              stringValue = node.getUserVisibleName()+((versRule==null||!versRule.
                  checkTo(where.getNode(), node, key))?"":(getVersionString(node.getTreeDB(), old, versS," ")));
            }
            if(!node.isSameTree(where.getNode())){
              if(trees == null)
                trees = new HashMap<>();
              trees.put(node.getUUId(), node.getTreeDB().getProjectName());
              if(persist)
              {
                stringValue = node.getTreeDB().getProjectName()+"::"+stringValue;
                //if(versS!=null)
//                  stringValue = stringValue+((versRule == null||!versRule.checkTo(where.getNode(), node, key))?"":getVersionString(old, versS," "));
              }
            }
          }else
          {
            ret = new UUID[lvars.size()];
            int i = 0;
            StringBuilder sb = new StringBuilder();
            if(persist){
              IReferenceVersionRule rvr = 
                  where.getNode().getTreeDB().getAttributeRules().getReferenceVersionRule();
              if(rvr!=null && rvr.checkFrom(where.getNode(), key) && getStoredNodeVers() != null && !versInited)
              {
                for(TreeNode node: lvars)
                  if(rvr.checkTo(where.getNode(),node, key))
                    getStoredNodeVers().put(node.getUUId(), node.getNodeVersion());
                this.oldNodes = nodes;
                this.versInited = true;
              }
              trees = new HashMap<>();
            }
            if(vers!=null && resVers == null)
              resVers = new HashMap<>();
            for(TreeNode node:lvars){
              ret[i++] = node.getUUId();
              if(resVers != null)
                resVers.put(ret[i-1], node.getNodeVersion());
              if(getStoredNodeVers()!=null)
                getStoredNodeVers().put(ret[i-1], node.getNodeVersion());
              if(persist){
                if(sb.length()>0)
                  sb.append(", ");
                if(!node.isSameTree(where.getNode())){
                  sb.append(node.getTreeDB().getProjectName());
                  sb.append("::");
                  trees.put(node.getUUId(),node.getTreeDB().getProjectName());
                }
                sb.append(node.getUserVisibleName());
                
                String versS = null;
                if(getStoredNodeVers()!=null){
                  String old = null;
                  if(versInited)
                    old = getStoredNodeVers().get(node.getUUId());
                  versS = node.getNodeVersion();
                  if(versS != null && (old != null && !versS.equals(old)))
                    markVersOutdated = true;
                  addWarn(new ReferenceOutdateMark(where.getNode().getTreeDB().getProjectName(), where.getNode().getUUId(), node.getTreeDB().getProjectName(), node.getUUId(),
                      where.getNode().getUserVisibleName(),
                      node.getUserVisibleName(),key, old, versS));
               //   else
                 //   getStoredNodeVers().put(node.getUUId(),versS);
                  sb.append((versRule == null||!versRule.checkTo(where.getNode(), node, key)?"":getVersionString(node.getTreeDB(), old, versS," ")));
                }
              }
            }
            if(persist){
              stringValue = "["+sb.toString()+"]";
              nodes = ret;
            }
            //nodes = lvars.toArray(new TreeNode[0]);
          }
          return ret;
        }
      }
      return null;
    }
    finally{
      if(rp!=null){
        rp.handleRVResolving(where, raw, nodes, getStoredNodeVers(), getWarns(), persist && rpFirst);
      }
    }
  }
  private Map<UUID, String> getStoredNodeVers() {
    return vers;
  }
  public String getVersionString(TreeDB db, UUID node, String prefix) {
    if(vers == null || resVers == null)
      return "";
    String old = vers.get(node), versS = resVers.get(node);
    if(old == null || versS == null)
      return "";
    return getVersionString(db, old, versS, prefix);
  }
  public Map<UUID, String> getVersions() {
    return vers;
  }
  public static String getVersionString(TreeDB db, String old, String versS, String prefix) {
    if(versS==null)
      return "";
    if(!AttributesVersioningRule.isRefVersEnabled(db))
      return "";
    if(versS.equals(AttributesVersioningRule.VALUE_NONE))
      versS = "N";
    if(old == null || old.equals(AttributesVersioningRule.VALUE_NONE))
      old = "N";
    return prefix+"[v"+(!old.equals(versS)?""+old+"←":"")+versS+"]";
  }
  @Override
  public String toString() {
    if(as!=null)
      return toString(as);
    return stringValue!=null ? stringValue : "Unresolved "+raw;

  }
  public String getRaw() {
    return raw;
  }
  public String getKey() {
    return key;
  }
  @Override
  public String toString(AttributeStorage where) {
    if(stringValue == null)
      resolve(where, true);
    return stringValue;
  }
  public boolean hasErrorsOnResolve(AttributeStorage where) {
    if(stringValue == null)
      resolve(where, true);
    return isError;
  }
  public static List<TreeNode> getValues(AttributeStorage where, AttributeType type, Object value) {
    if(value == null)
      return Collections.emptyList();
    if(type == AttributeType.REFERENCE){
      ReferenceValue rv = (ReferenceValue)value;
      TreeNode[]nodes = rv.getNodes(where);
      if(nodes == null)
        return null;
      List<TreeNode> ret = new LinkedList<>();
      for(TreeNode node:nodes)
        ret.add(node);
      return ret;
    }
    List<TreeNode> ret = new LinkedList<>();
    ListValue lv = (ListValue)value;
    for(Object o:lv){
      List <TreeNode> conv = getValues(where, lv.getType(), o);
      if(conv!=null)
        ret.addAll(conv);
    }
    return ret;
  }
  public static Map<TreeNode, String> getValuesMap(AttributeStorage where, AttributeType type, Object value) {
    if(value == null)
      return Collections.emptyMap();
    if(type == AttributeType.REFERENCE){
      ReferenceValue rv = (ReferenceValue)value;
      TreeNode[]nodes = rv.getNodes(where);
      if(nodes == null)
        return null;
      Map<TreeNode, String> ret = new HashMap<>();
      for(TreeNode node:nodes)
        ret.put(node, rv.getStoredNodeVers(node));
      return ret;
    }
    Map<TreeNode,String> ret = new HashMap<>();
    ListValue lv = (ListValue)value;
    for(Object o:lv){
      Map <TreeNode, String> conv = getValuesMap(where, lv.getType(), o);
      if(conv!=null)
        ret.putAll(conv);
    }
    return ret;
  }
  public static void resetAttribute(AttributeStorage as , AttributeType at, Object val) {
    if(at.equals(AttributeType.REFERENCE)){
      ReferenceValue rv = (ReferenceValue)val;
      rv.nodes = null;
      rv.isError = false;
      rv.stringValue = null;
      rv.trees = null;
    }else if(at == AttributeType.LIST){
      ListValue lv = (ListValue)val;
      for(Object o:lv.getValues())
        resetAttribute(as, lv.getType(), o);
    }
  }
  public String getStringValue(){
    return stringValue;
  }
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ReferenceValue))
      return false;
    ReferenceValue alt = (ReferenceValue) obj;
    if(!alt.key.equals(key))
      return false;
    //TODO check
    if(alt.getStoredNodeVers() == null ^ getStoredNodeVers() == null || getStoredNodeVers()!=null && !getStoredNodeVers().equals(alt.getStoredNodeVers()))
      return false;
    if(alt.stringValue!=null && stringValue!=null) {
      String sa = alt.toString(), sn = toString();
      if(sa==null ^ sn == null || sa!=null && sn!=null && !sn.equals(sa))
        return false;
    }
    return raw.equals(alt.raw);//caches are ignored
  }
  public static ReferenceValue fromString(String s, String attrLocations) {
    //TODO load
    return new ReferenceValue(s, attrLocations);
  }
//  public static Map<UUID, String> getLinkAttributeStoredVersions(
//      AttributeStorage as, Attribute a) {
//    Map<UUID, String> nv = null;
//    if(a.getType() == AttributeType.LIST){
//      for(Object o:((ListValue)a.getGValue()).getValues())
//        ((ReferenceValue)o).getStoredNodeVersion();
//    }else
//      if(a.getType() == AttributeType.REFERENCE){
//        
//      }
//    return null;
//  }
  private Map<UUID,String> getStoredNodeVersions() {
    return getStoredNodeVers();
  }
  @Override
  public void setStringParam(String param, String value) {
    if(param.startsWith(VERSION_PREFIX)){//TODO
      param = param.substring(VERSION_PREFIX.length());
    }
  }
  @Override
  public Map<String, String> getStringParams() {
    return null;
  }
  public static ReferenceValue newEmpty() {
    return new ReferenceValue("", null);
  }
  public static ReferenceValue getOne(TreeNode node, Object result, String key) {
    IReferenceVersionRule r = node == null?null: node.getTreeDB().getAttributeRules().getReferenceVersionRule();
    return getOne(node!=null?node.attrs():null,result, key, r);
  }
  public static ReferenceValue getOne(TreeNode node, Object result, String key, UUID[]oldNodes, Map<UUID, String> vers) {
    IReferenceVersionRule r = node == null?null:node.getTreeDB().getAttributeRules().getReferenceVersionRule();
    return new ReferenceValue(result, key, oldNodes, vers, r);
    
  }
  public static ReferenceValue getOne(AttributeStorage as, Object result, String key, IReferenceVersionRule initVers) {
    //TODO cache in links resolver
    return new ReferenceValue(as,result, key, initVers);
  }
  public static Map<UUID, String> getLinkAttributeStoredVersions(
      AttributeStorage attributeStorage, Attribute a) {
    Map<UUID, String> verss = new HashMap<>();
    handleAttributeVal(verss, a.getType(), a.getValue());
    return verss;
  }
  public static void handleAttributeVal(Map<UUID, String> verss, AttributeType at, Object val){
    if(at == AttributeType.LIST)
    {
      ListValue lv = (ListValue)val;
      for(Object o: lv.getValues())
        handleAttributeVal(verss, lv.getType(), o);
    }else
      if(at == AttributeType.REFERENCE){
        ReferenceValue rv = (ReferenceValue)val;
        if(rv.getStoredNodeVers()!=null)
          verss.putAll(rv.getStoredNodeVers());
      }
  }
  public String getStoredNodeVers(TreeNode node) {
    if(vers!=null)
      return vers.get(node.getUUId());
    return null;
  }
  public UUID[] getStoredNodes() {
    return oldNodes;
  }
  public CopyOnWriteArrayList<IMark> getWarns() {
    return warns;
  }
  public void initProviderLink(ReferenceValueProvider rp, boolean rpFirst) {
    this.rp = rp;
    this.rpFirst = rpFirst;
  }
  public void addWarn(ReferenceOutdateMark referenceOutdateMark) {
    if(warns == null)
      warns = new CopyOnWriteArrayList<>();
    warns.add(referenceOutdateMark);
  }
  public List<IMark> updateString(AttributeStorage as) {
    List<IMark> ret = null;
    if(warns!=null)
      ret = warns;
    warns = null;
    nodes = null;//TODO more proper
    stringValue = null;
    getNodes(as, true);
    return ret;
  }
}
