package com.unitesk.requality.core.attribute;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public class SystemNames {
  public final static String SEQUENTIAL_ID_REF_NAME="%SeqID";
  private Map<String,String> smap = new HashMap<>(),
                revs = new HashMap<>();
  private Map<String, AttributeType> tmap = new HashMap<>();
  private TreeDB db;
//  private static Map<String, SystemNames> sn = new HashMap<>();
  private static String META_SYS_NAMES = "ATTRIBUTE_NAME_SYNOMS";
  private static SystemNames DEF_SYSNAMES = new SystemNames(null);
  public static SystemNames getOne(TreeDB db){
    if(db!=null) {
      SystemNames sna = db.getMetaObject(META_SYS_NAMES);
      if(sna == null){
        //TODO location?
        sna = new SystemNames(db);
        db.registerMetaObject(META_SYS_NAMES, sna);
      }
      return sna;
    }else
      return DEF_SYSNAMES;
  }
  
  public SystemNames(TreeDB db){
    this.db = db;
    addValue(TreeNode.ATTR_NAME, "%Name", AttributeType.STRING);
    addValue(TreeNode.ATTR_NAMEORID, "%NameOrId", AttributeType.STRING);
    addValue(TreeNode.ATTR_TYPE, "%Type", AttributeType.STRING);
    addValue(TreeNode.ATTR_ID, "%Id", AttributeType.STRING);
    addValue(TreeNode.ATTR_USERVISIBLEID, "%UVID", AttributeType.STRING);
    addValue(TreeNode.ATTR_DESCRIPTION, "%Description", AttributeType.HTML);
    addValue(AttributesVersioningRule.ATTR_NODE_VERSION, "%Version", AttributeType.STRING);
    addValue(TreeNode.ATTR_CHILDREN, "%Children", AttributeType.LIST);
    addValue(TreeNode.ATTR_PARENT, "%Parent", AttributeType.STRING);
    addValue(TreeNode.ATTR_LABEL, "%Label", AttributeType.STRING);
    for(Entry<String, String> s:smap.entrySet())
      revs.put(s.getValue(), s.getKey());
    smap = Collections.unmodifiableMap(smap);
    tmap = Collections.unmodifiableMap(tmap);
  }
  public String getVisSysName(String name){
    if(name.equals(FIDUpdater.getFIDName(db)))
      return SystemNames.SEQUENTIAL_ID_REF_NAME;
    return smap.get(name);
  }
  private void addValue(String attrName, String altName, AttributeType type) {
    smap.put(attrName, altName);
    tmap.put(attrName, type);
  }
  public Collection<String> getVisSysNames(){
    Set<String>s = new HashSet<String>(revs.keySet());
    s.add(SEQUENTIAL_ID_REF_NAME);
    return s;
  }
  public String getSysName(String text) {
    if(SEQUENTIAL_ID_REF_NAME.equals(text))
      return FIDUpdater.getFIDName(db);
    return revs.get(text);
  }
  public Map<String,String> getVisSysNamesMap() {
    Map<String, String> map = new HashMap<>(smap);
    map.put(FIDUpdater.getFIDName(db),SEQUENTIAL_ID_REF_NAME);
    return smap;
  }
  public AttributeType getSysType(String sysName) {
    if(SEQUENTIAL_ID_REF_NAME.equals(sysName))
      return AttributeType.STRING;
    return tmap.get(sysName);
  }
  public Set<String> getSysNames() {
    Set<String>s = new HashSet<String>(smap.keySet());
    s.add(FIDUpdater.getFIDName(db));
    return s;
  }

  public boolean isSystemName(String vname) {
    Set<String> set = getSysNames();
    if(set.contains(vname))
      return true;
    Collection<String> vset = getVisSysNames();
    if(vset.contains(vname))
      return true;
    return false;
  }
}
