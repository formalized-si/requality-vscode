package com.unitesk.requality.core.attribute;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.exception.OrderWasAlreadySetException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.OFlag;

public class VersionsStorage {
  public enum Type {
    STRUCT,
    ATTRIBUTES
  }
  private ChangesSet<Version> changed = new ChangesSet<>();
  private AttributeStorage storage;
  private Map<String, Version> vers = null;
  private Map<String, Version> overs = new HashMap<>();
  // private Map<String,Version> avers = new HashMap<>();
  private static Map<String, Version> defs = new HashMap<>();

  static {
    defs.put(getVersionKey(Type.ATTRIBUTES), Version.getZero());
    defs.put(getVersionKey(Type.STRUCT), Version.getZero());
    defs.put(getParentVersionKey(Type.ATTRIBUTES), Version.getMinusOne());
  }

  private static Map<String, Version> vdefs = new HashMap<>();

  static {
    vdefs.put(getCloneParentVersionKey(Type.STRUCT), Version.getMinusOne());
    vdefs.put(getCloneParentVersionKey(Type.ATTRIBUTES), Version.getMinusOne());
  }

  protected VersionsStorage(AttributeStorage as) {
    storage = as;
  }

  public Map<String, Version> getVers(boolean force) {
    if (vers == null) {
      vers = new HashMap<>();
      for (Entry<String, Version> en : defs.entrySet()) 
        vers.put(en.getKey(), en.getValue().clone());
      if (storage.getNode().isVirtual())
        for (Entry<String, Version> en: vdefs.entrySet())
          vers.put(en.getKey(), en.getValue().clone());

      force = true;
    }
    if (force) {
      vers.put(getVersionKey(Type.ATTRIBUTES), getVersion(getVersionKey(Type.ATTRIBUTES)));
      vers.put(getVersionKey(Type.STRUCT), getVersion(getVersionKey(Type.STRUCT)));
    }
    return vers;
  }

  public void saveVers() {
    if(!changed.isEmpty()){
  //    Map<String, Version> sst = storage.getNode().getTreeDB().getStorage().getVersions(storage.getNode().getUUId());
      Map<String, Version> stored = getVers(true),
          map = new HashMap<>();
      for (Entry<String,Version> en : stored.entrySet())
        map.put(en.getKey(), en.getValue().clone());
      storage.getNode().getTreeDB().getStorage().saveVersions(storage.getNode().getUUId(),changed, OFlag.NONE);
      changed.clear();
      overs.clear();
    }
  }

  private Map<String, Version> getVers() {
    return getVers(false);
  }

  public Version getVersion(String key) {
    return getVersion(key, Version.getUnset());
  }
  public Version getVersion(String key, Version def) {
    return getVersion(key, def, true);
  }

  private Version getVersion(String key, Version def,boolean regen) {
    if(regen){
      if (key.equals(getVersionKey(Type.ATTRIBUTES))) 
        return new Version(storage.hashCode());
      if (key.equals(getVersionKey(Type.STRUCT))) {
        return new Version(storage.getNode().getActiveWorkChildrenUUIDs().hashCode());
      }
    }
    Map<String, Version> vs = getVers(false);

    Version v = vs.get(key);

    if (v != null) return v;
    return def.clone();
  }

  public static Version getVersion(VersionsStorage as, String tp) {
    return as.getVersion(tp);
  }

  public static Version getVersion(VersionsStorage as, Type tp) {
    return getVersion(as, getVersionKey(tp));
  }

  public static Version getParentVersion(VersionsStorage as, Type tp) {
    return getVersion(as, getParentVersionKey(tp));
  }

  public Version getParentVersion(Type tp) {
    return getParentVersion(this, tp);
  }

  public static Version getCloneParentVersion(VersionsStorage as, boolean hasCP, Type tp) {
    if (!hasCP) return null;
    return getVersion(as, getCloneParentVersionKey(tp));
  }

  public Version getCloneParentVersion(Type tp) {
    return getCloneParentVersion(this, true, tp);
  }

  public void setVersion(Type tp, Version v, boolean save) {
    setVersion(getVersionKey(tp), v, save);
  }

  public void setVersion(String key, Version version, boolean save) {
    Version v = getVersion(key);
    boolean changes = false;
    //TODO mb remove
    if(!overs.containsKey(key))
      if(v != null)
        overs.put(key, v.clone());
      else
        overs.put(key, null);
    if (v == null || v.isUnset()) {
      v = Version.getZero();
      getVers().put(key, v);
    }
    if(version == null){
      try {
        changed.addSomeChange(key,new Version(v.getInt()),null);
        changes = true;
        getVers().remove(key);
      } catch (OrderWasAlreadySetException e) {
        RLogger.logError(e);
      }
    }else{
      changes = changes || v.getInt()==null || !v.getInt().equals(version.getInt());
      if(v.getInt()==null || v.getSInt()!=version.getSInt())
        try {
          changed.addSomeChange(key,new Version(v.getInt()),version);
        } catch (OrderWasAlreadySetException e) {
          RLogger.logError(e);
        }
      getVers().get(key).setInt(version.getInt());
    }
    
    if (save && changes) saveVers();
  }

  public void setParentVersion(Type tp, Version v) {
    final String key = getParentVersionKey(tp);
    getVers().put(key, v);
    saveVers();
  }

  public void setCloneParentVersion(Type tp, Version v) {
    setCloneParentVersion(tp, v, false);
  }

  public void setCloneParentVersion(Type tp, Version v, boolean ignoreup) {
    final String key = getCloneParentVersionKey(tp);
    getVers().put(key, v);
    saveVers();
  }

  public void updateCloneParentVersion(Type tp) {
    TreeNode node = storage.getNode().getCloneParent();
    if (node == null) throw new RuntimeException("Clone parent loading problems!"); // node
    // loading
    // problems?
    VersionsStorage vs = node.attrs().getVersionsStorage();
    if (vs == null) throw new RuntimeException("Clone parent loading problems!"); // node
    // loading
    // problems?
    Version v = vs.getVersion(getVersionKey(tp)).clone();
    final String key = getCloneParentVersionKey(tp);
    getVers().put(key, v);
    saveVers();
  }

  public void updateParentVersion(Type tp) {
    TreeNode par = storage.getNode().getWorkParent();
    if (par == null) return;
    VersionsStorage as = par.attrs().getVersionsStorage();
    if (as == null) throw new RuntimeException("Parent loading problems!"); // node
    // loading
    // problems?
    Version v = as.getVersion(getVersionKey(tp)).clone();
    final String key = getParentVersionKey(tp);
    getVers().put(key, v);
    saveVers();
  }

  public boolean isParentVersionOutdated(Type tp) throws Exception {
    TreeNode par = storage.getNode().getWorkParent();
    if (par == null) return false;
    VersionsStorage as = par.attrs().getVersionsStorage();
    Version cur = getParentVersion(tp);
    Version v = as.getVersion(getVersionKey(tp));
    Version parv = v;
    return !parv.equals(cur);
  }

  public boolean isCloneParentVersionOutdated(Type tp) throws Exception {
    TreeNode cp = storage.getNode().getCloneParent();
    if (cp == null) return false;
    VersionsStorage vs = cp.attrs().getVersionsStorage();
    Version v = vs.getVersion(getVersionKey(tp));
    Version cur = getCloneParentVersion(tp);
    return !cur.equals(v);
  }

  private static String getVersionKey(Type tp) {
    return tp.equals(Type.STRUCT) ? "_%sversion" : "_%aversion";
  }

  private static String getParentVersionKey(Type tp) {
    return tp.equals(Type.STRUCT) ? "_%spversion" : "_%apversion";
  }

  private static String getCloneParentVersionKey(Type tp) {
    return tp.equals(Type.STRUCT) ? "_%scpversion" : "_%acpversion";
  }

  public void incVersion(Type struct) {
    Version v = getVersion(struct);
    v.inc();
    setVersion(struct, v, true);
  }

  public void updateVersion(Type struct) {
    setVersion(struct, getVersion(getVersionKey(struct),Version.getUnset(),false), true);
  }

  public static Version getVersion(TreeDB db, Type tp, UUID where) {
    TreeNode node = db.getNode(where);
    if (node == null) return null;
    VersionsStorage vs = node.attrs().getVersionsStorage();
    if (vs == null) return null;
    return getVersion(vs, tp);
  }

  public static boolean isCloneParentVersionOutdated(TreeDB db, Type tp, UUID where, UUID cp) {
    TreeNode node = db.getNode(where);
    if (node == null) return false;
    VersionsStorage vs = node.attrs().getVersionsStorage();
    try {
      return vs.isCloneParentVersionOutdated(tp);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return false;
  }

  public static boolean isParentVersionOutdated(TreeDB db, Type tp, UUID where) {
    TreeNode node = db.getNode(where);
    if (node == null) return false;
    VersionsStorage vs = node.attrs().getVersionsStorage();
    if (vs == null) return false;
    try {
      return vs.isParentVersionOutdated(tp);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return false;
  }

  public Version getVersion(Type struct) {
    return getVersion(struct, Version.getUnset());
  }

  public Version getVersion(Type struct, Version def) {
    return getVersion(getVersionKey(struct), def, false);
  }

  public void reload() {
    Set<String> keys = getKeys();
    Map<String, Version> stored =
        new HashMap<>(
            storage.getNode().getTreeDB().getStorage().getVersions(storage.getNode().getUUId()));
    keys.removeAll(stored.keySet());
    for (String s : keys) vers.remove(s);
    for (String s : stored.keySet()) vers.put(s, stored.get(s).clone());
  }

  public Set<String> getKeys() {
    return new HashSet<>(getVers().keySet());
  }

  public void setVersions(Map<String, Version> attributes) {
    vers = new HashMap<>(attributes);
  }

  public void mergeVersions(Map<String, Version> attrs) {
    for (String s : attrs.keySet()) vers.put(s, attrs.get(s));
  }

  public static Map<String, Version> getDefaults() {
    return defs;
  }

  public Set<String> getAKeys() {
    return new HashSet<>(vers.keySet());
  }
}
