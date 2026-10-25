package com.unitesk.requality.core.attribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.regex.Pattern;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.unitesk.requality.core.attribute.VersionsStorage.Type;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.GeneratorValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.attribute.valueprovider.ValueProviderFactory;
import com.unitesk.requality.core.exception.OrderWasAlreadySetException;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.tool.CoreUtils;

public class AttributeStorage implements Iterable<Attribute> {
  private TreeNode node;
  //public TreeDB db;
  private ReentrantReadWriteLock arwl = new ReentrantReadWriteLock();
  private WriteLock wl = arwl.writeLock();
  private ReadLock rl = arwl.readLock();  
  private Map<String, Attribute> attributes = new ConcurrentSkipListMap<>();
  //private Map<String, Attribute> virtAttrs = new HashMap<>();
  // public Map<String, Attribute> attributesO =null;

  // private static Map<TreeDB,Map<UUID,AttributeStorage>> dbstorages =
  // new HashMap<TreeDB,Map<UUID,AttributeStorage>>();
  private ChangesSet<AttributeDefinition> changes = new ChangesSet<>();
  private ChangesSet<Attribute> attrTemp = null;
  private VersionsStorage vs;

  @Override
  public int hashCode() {
    ReadLock l = arwl.readLock();
    try {
      l.lock();
      return node.getUUId().hashCode() ^ changes.hashCode() ^ attributes.hashCode();
    } finally {
      l.unlock();
    }
  }
  public Attribute[] getAttributesByPattern(String pattern){
    List<Attribute> attrs = new LinkedList<>();
    Pattern p = Pattern.compile(pattern);
    for(String s:getAllAttributeKeys())
      if(p.matcher(s).matches())
        attrs.add(getAttribute(s));
    return attrs.toArray(new Attribute[0]);
  }
  private AttributeStorage(TreeNode master, TreeDB db) {
    setNode(master);
    //this.db = db;
    vs = new VersionsStorage(this);
    // if(!dbstorages.containsKey(db))
    // dbstorages.put(db, new HashMap<UUID,AttributeStorage>());
    // //TODO reloading issues
    // if(dbstorages.get(db).containsKey(master.getUUId()))
    // throw new RuntimeException("Attempt to init already initiated node");
    // dbstorages.get(db).put(master.getUUId(),this);
  }

//  public List<TreeNode> resolveStringRef(String key, String path) {
//    List<TreeNode> rel =
//        getNode()
//            .getTreeDB()
//            .getLinkResolver()
//            .resolvePath(
//                getNode(), key, getNode().getTreeDB(), path, Path.UUID | Path.XPATH | Path.UV);
//    if (rel != null && rel.size() > 0) return rel;
//    return null;
//  }

  public void setChangingAttributes(ChangesSet<Attribute> ret) {
    synchronized(this) {
      attrTemp = ret;
    }
  }
  public static AttributeStorage loadOrCreate(TreeNode master, TreeDB db) {
    if (db == null) return new AttributeStorage(master, db);
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    synchronized (master.getUUId()) {
      if (db.getCache().containsKey(master.getUUId()))
        return db.getCache().get(master.getUUId()).attrs();
      // if(dbstorages.containsKey(db)&&dbstorages.get(db).containsKey(master.getUUId()))
      // return dbstorages.get(db).get(master.getUUId());
      return new AttributeStorage(master, db);
    }
  }

  private boolean correctNested(AttributeDefinition ad) {
    if (!getNode().getUUId().equals(ad.getOrigin())) {
      if(ad.getOrigin() == null)
        return true;
      return ad.isAvaiableOn(getNode(), !ad.getOrigin().equals(getNode().getUUId()));
    }
    return true;
  }
  /**
   * Put attribute to this element. If name of attribute is equal to one stored in element then
   * stored attribute will be replaced by new attribute. To save all changes over attributes,
   * saveAttributes() method needs to be called
   *
   * @param a attribute to add or replace
   * @return value of old attribute with same name
   */
  
//  public void storeAttribute(Attribute a, boolean first) {
//    if (!correctNested(a.getDefinition())) return;
//    // TODO checl
////    AttributeGenerator ag = a.getGenerator();
////    if (ag != null) {
////      ag.setAttribute(a);
////      ag.updateValue(this); // TODO change
////    }
//    // last attribute value
////    Attribute old = getLocalAttribute(a.getKey());
////    if (old != null
////        && a != null
////        && (!a.getVersion().isUnset()
////            && (a.getVersion().equals(old.getVersion()) && a.getOrigin().equals(old.getOrigin()))))
////      return null;
////    if (old != null
////        && old.isMoreVisible(node.getTreeDB(), a, node.getUUId(), false)
////        && !old.getOrigin().equals(node.getUUId())) return null; // old is more vis
////    if (!a.checkTypeValue(this))
////      throw new RuntimeException(
////          "Attribute "
////              + a.getKey()
////              + " has value incorrect for selected type "
////              + a.getResultType()
////              + "!"
////              + (a.getListType() != null ? ("or list type - " + a.getListType()) : ""));
////    if (old == null ^ a == null || !old.equals(a))
////      rawStoreAttribute(a);
////    else
////      return null;
////    return old;
//    rawStoreAttribute(a);
//  }


  /**
   * Remove attribute with given attribute name. To save all changes over attributes,
   * saveAttributes() method needs to be called
   *
   * @param key name of attribute to delete
   * @return removed attribute
   */
  public Attribute removeAttribute(String key) {
    return removeAttribute(key, false);
  }

  // public Attribute removeAttribute(UUID origin, String key) {
  // return removeAttribute(origin,key,false,true);
  // }
  public Attribute removeAttribute(String key, boolean ignoreReplace) {
    // Attribute back = getLocalAttribute(key);
    Attribute a = getAttribute(key);
    AttributeDefinition vp = null;
    if(a!=null)
      vp = a.getDefinition();
    if(vp == null)
      vp = getWorkAttributeDefinition(key);//not commited attr 
    boolean ch = false;
    if(!ignoreReplace){
      AttributeDefinition b = findSuitable(key);
      if (b != null) {
        setAttribute(b);
        try {
          changes.addSomeChange(key, vp, b);
        } catch (OrderWasAlreadySetException e) {
          RLogger.logError(e);
        }
        ch = true;
      }
      if(!ch){
        ch = false;
        if(vp!=null)
          try {
            changes.addSomeChange(key, vp, null);
            ch = true;
          } catch (OrderWasAlreadySetException e) {
            RLogger.logError(e);
          }
        //changes.addSomeChange(key, vp, null);
      }
//      if(ach&&vp!=null){
//        vp.getValueProvider().detach(getNode());
//        if(b!=null)
//          b.getDefinition().getValueProvider().attach(getNode());
//      }
    }
    return a;
  }

  public AttributeDefinition getWorkAttributeDefinition(String key) {
    Attribute a = null;
    try{ 
      readLock();
      if(changes.contains(key))
        return changes.getNew(key);
       a = attributes.get(key);
    }
    finally{
      readUnlock();
    }
    if(a!=null)
      return a.getDefinition();
    return getNode().getVirtualAttributeDefinition(key);
  }

  private void readUnlock() {
    rl.unlock();
  }

  private void readLock() {
    rl.lock();
  }

  private void writeLock() {
    wl.lock();
  }
  private void writeUnlock() {
    wl.unlock();
  }

  private AttributeDefinition findSuitable(String key) {
    Set<UUID> srs = Instantiator.getRootTree(getNode().getTreeDB()).attrRootCache.get(key);
    if (srs != null) {
      AttributeDefinition best = null;
      TreeDB rootDB = getNode().getTreeDB();
      if(rootDB!=null && rootDB.isVirtual())
        rootDB = Instantiator.getRootTree(rootDB);
      for (UUID uid : srs) {
        TreeNode sr = getNode().getTreeDB().getNode(uid);
        if (sr != null&&!sr.equals(getNode())) {
          Attribute dl = sr.attrs().getAttribute(key);
          if (dl != null && dl.getDefinition().isAvaiableOn(getNode(),false))
            if (best == null || dl.getDefinition().isMoreVisible(rootDB, best, getNode().getUUId(), false)) 
              best = dl.getDefinition();
        }
      }
      if (best != null) return best;
    }
    if (getNode().getCloneParent() != null) {
      Attribute a = getNode().getCloneParent().attrs().getAttribute(key);
      if (a != null) return a.getDefinition();//TODO check
    }
    AttributeDefinition ad = 
      getNode().getTreeDB().getAttributeRules().getRequiredAttrRule().
        getDefinition(getNode().getType(), key);
    if(ad!=null)
      return new AttributeDefinition(getNode().getUUId(),key,
          AttributeDefinition.detectValueProvider(key, ad.getType(), ad.getRawValue().getValue(),
            TempFolderFilesProvider.getNewNotInited(ad.getType())),
          ad.getFlags());
    return null;
  }

  private AttributeDefinition removeFromAttributeList(String key) throws OrderWasAlreadySetException {
    WriteLock rl = arwl.writeLock();
    AttributeDefinition old = null;
    try {
      rl.lock();
      old = changes.getNew(key);
      if(old == null){
        Attribute o = attributes.get(key);
        if(o!=null)
          old = o.getDefinition();
      }
    } finally {
      rl.unlock();
    }
    if(old!=null)
      changes.addSomeChange(key, old, null);
    return old;
  }

  public Attribute actualRemoveFromAttributeList(String key) {
    try {
      writeLock();
//      AttributeDefinition old = changes.getNew(key);
//      if(old == null){
//        Attribute o = attributes.get(key);
//        if(o!=null)
//          old = o.getDefinition();
//      }
//      if(old!=null)
//        changes.addSomeChange(key, old, null);
      Attribute b = attributes.remove(key);
      if(b!=null)
      {
        b.getDefinition().getValueProvider().detach(getNode());
        unregisterVar(b);
      }
      return b;
    } finally {
      writeUnlock();
    }
  }

  /**
   * Get attribute with given attribute name.
   *
   * @param key name of attribute
   * @return attribute with given name
   */
  public Attribute getAttribute(String key) {
    readLock();
    Attribute a = null;
    try {
      if(attrTemp!=null)
        synchronized(this) {
          if(attrTemp!=null && attrTemp.contains(key))
            return attrTemp.getNew(key);
        }
      a = attributes.get(key);
    }finally {
      readUnlock();
    }
    if(a == null){
      String an = SystemNames.getOne(node!=null?node.getTreeDB():null).getSysName(key);
      if(an!=null){
        key = an;
        readLock();
        try {
          a = attributes.get(key);
        }finally {
          readUnlock();
        }
      }
    }
    if (a==null){
      try{
        readLock();
        try{
          a = attributes.get(key);
          if(a!=null)
            return a;
        }finally{
          readUnlock();
        }
        AttributeDefinition ad = getNode().getVirtualAttributeDefinition(key);
        if(ad!=null){
          Attribute va = ad.toAttribute(getNode());
    	  if(va == null){
            AttributeDefinition glob = findSuitable(key);
            if(glob!=null)
              va = glob.toAttribute(getNode());
          //actualRemoveFromAttributeList(key);
          }
          try{
            writeLock();
            if(va!=null)
              putToAttributes(va);//TODO change?
            return attributes.get(key);
          }finally{
            writeUnlock();
          }
        }
        return null;
      }catch(Exception e){
        RLogger.logError(e);
      }
    }
    return a;
  }

  /**
   * Get set of names of available attributes
   *
   * @return set of names of attributes
   */
  public Set<String> getLocalAttributeKeys() {
    ReadLock rl = arwl.readLock();
    try {
      rl.lock();
      return Collections.unmodifiableSet(attributes.keySet());
    } finally {
      rl.unlock();
    }
  }

  public Set<String> getAllAttributeKeys() {
    ReadLock rl = arwl.readLock();
    TreeSet<String> attrs = null;
    try {
      rl.lock();
      attrs = new TreeSet<>(getLocalAttributeKeys());
    } finally {
      rl.unlock();
    }
    attrs.addAll(ValueProviderFactory.getVirtAttrsNames());
    return attrs;
  }

  public Set<String> getWorkAttributeKeys(boolean withVirt) {
    Set<String> attrs = null;
    ReadLock rl = arwl.readLock();
    try {
      rl.lock();
      attrs = new HashSet<>(getLocalAttributeKeys());
      attrs.addAll(changes.getAdded());
    } finally {
      rl.unlock();
    }
    if(withVirt)
      attrs.addAll(ValueProviderFactory.getVirtAttrsNames());
    return attrs;
  }

  public Map<String, Attribute> getLMap() {
    return getMap(true);
  }

  public Map<String, Attribute> getMap() {
    return getMap(false);
  }
  /**
   * Get map of available attributes. Key is attribute name, value - corresponding attribute
   *
   * @return map of attributes
   */
  public Map<String, Attribute> getMap(boolean local) {
    if (local) return getLocalAttributes();
    Map<String, Attribute> map = new HashMap<>();
    for (Attribute a : this) {
      map.put(a.getKey(), a);
    }
    return map;
  }

  public Map<String, Attribute> getLocalAttributes() {
    //Map<String, Attribute> retMap = new HashMap<String, Attribute>();
    ReadLock rl = arwl.readLock();
    try {
      rl.lock();
      return Collections.unmodifiableMap(attributes);
//      keys = new HashSet<String>(attributes.keySet());
//      for (String s : keys)
    } finally {
      rl.unlock();
    }
  }
  public static boolean attrsDefsEquals(AttributeDefinition a, AttributeDefinition b) {
    //TODO test
    if (a == null) return b == null;
    else return b != null && a==b;
  }
  public static boolean attrsEquals(Attribute a, Attribute b) {
    if (a == null) return b == null;
    else return b != null && a.equals(b);
  }

//  public static List<Attribute> mergeAttrs(
//      TreeDB db, List<Attribute> oldL, Collection<Attribute> newL, UUID where) {
//    return mergeAttrs(db, oldL, newL, where, false);
//  }
//
//  public static List<Attribute> mergeAttrs(
//      TreeDB db,
//      List<Attribute> oldL,
//      Collection<Attribute> newL,
//      UUID where,
//      boolean ignoreLocal) {
//    Iterator<Attribute> itold = null, itnew = null;
//    if (oldL != null) itold = oldL.iterator();
//    if (newL != null) itnew = newL.iterator();
//    List<Attribute> merged = new LinkedList<>();
//    Attribute n = null, o = null;
//    while (itold != null && itold.hasNext() || itnew != null && itnew.hasNext()) {
//      if (o == null && itold != null && itold.hasNext()) o = itold.next();
//      if (n == null && itnew != null && itnew.hasNext()) n = itnew.next();
//      while (n != null && !ignoreLocal && n.getAvailability() == AttributeAvailability.LOCAL) {
//        n = null;
//        if (itnew != null && itnew.hasNext()) n = itnew.next();
//      }
//      if (o != null && n != null) {
//        if (o.getOrigin().equals(n.getOrigin())) {
//          merged.add(n);
//          o = null;
//          n = null;
//          // TODO several attrs on single?
//        } else {
//          if (o.isMoreVisible(db, n, where, true) && o.isMoreVisible(db, n, where, false)
//              || !n.getVersion().equals(o.getVersion())) {
//            merged.add(o);
//            o = null;
//          } else {
//            merged.add(n);
//            n = null;
//          }
//        }
//      } else {
//        if (o != null) {
//          merged.add(o);
//          o = null;
//        }
//        if (n != null) {
//          merged.add(n);
//          n = null;
//        }
//      }
//    }
//    return merged;
//  }

  // public static Object[] getRemovedAdded(UUID targ, Set<String>
  // attrSignChanges, Map<String,Attribute>oldAll,
  // Map<String,Attribute>newAll){
  // return getRemovedAdded(targ, attrSignChanges, oldAll, newAll,false);
  // }
  public static Object[] getChanges(
      TreeDB db,
      UUID targ,
      Set<String> attrSignChanges,
      Map<String, Attribute> oldAll,
      Map<String, Attribute> newAll) {
    List<Attribute> removed = new LinkedList<>(), added = new LinkedList<>();
    // changesVis = new LinkedList<Attribute>();
    Set<String> chVis = new HashSet<>();
    if (attrSignChanges == null) {
      attrSignChanges = new HashSet<>();
      if (oldAll != null) attrSignChanges.addAll(oldAll.keySet());
      attrSignChanges.addAll(newAll.keySet());
    }
    for (String s : attrSignChanges) {
      // Attribute o = oldVisible.get(s),n = getLocalAttribute(s,false);
      Attribute olda = oldAll == null ? null : oldAll.get(s), newa = newAll.get(s);
      boolean oldaf = true, newaf = true;
      do {
        if (olda != null)
          if (newa == null) {
            // add rems
            removed.add(olda);
            // if first - change vis
            if (oldaf && newaf && !s.startsWith("_%")) chVis.add(s);
            // next
            olda = null;
            oldaf = false;
          } else {
            if (!olda.getOrigin().equals(targ)) {
              if (olda.isMoreVisible(db, newa, targ, false)) {
                // add rems
                // removed.add(olda);
                // //if first - change vis
                // if(oldaf&&newaf&&!s.startsWith("_%"))
                // chVis.add(s);
                // olda=null;
                // oldaf = false;
                // ignore
              } else {
                // TODO false up
                // add add
                added.add(newa);
                // if first - change vis
                if (oldaf && newaf && !s.startsWith("_%")) chVis.add(s);
                // next
                newa = null;
                newaf = false;
              }
            } else {
              if (!olda.equals(newa)) {
                if (oldaf && newaf && !s.startsWith("_%")) chVis.add(s);
                added.add(newa);
              }

              newa = null;
              newaf = false;
              olda = null;
              // oldaf = false;
              // next
              // if(itnew!=null && itnew.hasNext())
              // newa=itnew.next();
              newaf = false;
            }
          }
        else if (newa == null) {
          // null key, khekh(
        } else {
          // add add
          added.add(newa);
          // if first - change vis
          if (oldaf && newaf && !s.startsWith("_%")) chVis.add(s);
          // next
          newa = null;
          // if(itnew!=null && itnew.hasNext())
          // newa=itnew.next();
          newaf = false;
        }
      } while (olda != null || newa != null);
      ;
    }
    return new Object[] {removed, added, chVis};
  }

  public static Map<UUID, Attribute> formByUUIDMap(UUID where, List<Attribute> list) {
    Map<UUID, Attribute> umap = new HashMap<>();
    for (Attribute a : list)
      if (a == null) continue;
      else if (a.getOrigin() == null) umap.put(where, a);
      else umap.put(a.getOrigin(), a);
    return umap;
  }

//  public void incVersions(
//      ChangesSet<Attribute> attrSignChanges, Map<String, Attribute> newAll, VersionsStorage vs) {
//    //TODO rewrite
//    for (String s : attrSignChanges.getAll()) {
//      Attribute newa = newAll.get(s);
//      Version os = null;
//      if (vs != null) os = vs.getVersion(s);
//      if (newa != null) {
//        Version v = newa.getVersion();
//        if (v.isUnset()) {
//          int newv = 1;
//          if (os != null && !os.isUnset())
//            newv = os.getInt() + 1;
//          else
//            if(vs!=null)
//              vs.setVersion(s, new Version(1), false);
//          newa.setVersion(newv);
//          if(vs!=null)
//            vs.setVersion(s, new Version(newv), false);
//        }
//      }
//      else{
//        if (vs != null) os = vs.getVersion(s);
//        if (os != null && !os.isUnset()) os.setInt(os.getInt() + 1);//inc on remove
//      }
//      if (newa != null){
//        if(os ==null){
//          if(vs!=null)
//            vs.setVersion(s, newa.getVersion().clone(), false);
//        }else{
//          os.setInt(newa.getVersion().getInt());
//          if(vs!=null)
//            vs.setVersion(s, newa.getVersion().clone(), false);
//        }
//      }
//    }
//  }

  public void commitAttributes() {
    if(changes.isEmpty())
      return;
    ChangesSet<Attribute> ach = TreeDB.addAttrsRec(this, true, false, OFlag.NONE);
    Map<String,AttributeDefinition> tosave = new HashMap<>();
    for(String s:ach.getOrder()){
      Attribute a = ach.getNew(s);
      if(a!=null)
        tosave.put(s, a.getDefinition());
      else
        tosave.put(s, null);
    }

    wl.lock();
    try {
      saveAttributes(ach, OFlag.NONE);
      clearChanges();
    }finally {
      wl.unlock();
    }
  }

  public void saveAttributes(ChangesSet<Attribute> changesSet, byte flag) {
//    WriteLock rl = arwl.writeLock();
//    try {
//      rl.lock();
//      //incVersions(changesSet, map, node.getVS());
//      getNode().getVS().saveVers();
//    } finally {
//      rl.unlock();
//    }
	int l = changesSet.getAll().size();
    wl.unlock();
    try {
	    getNode().getTreeDB().commitAttributes(getNode(), new ChangesSet<>(changesSet), true, true, flag);
    }finally {
    	wl.lock();
    }
    if(l!=changesSet.getAll().size())
      throw new RuntimeException("Concurrent save attempt for "+getNode().getUUId());
    changesSet.clear();
    if (!changes.isEmpty()) {
      getNode().getVS().updateVersion(Type.ATTRIBUTES);
      getNode().getVS().saveVers();
    }
  }

//  public Attribute setAttribute(
//      AttributeDefinition ad){
//    return setAttribute(ad);
//  }
  public Attribute setAttribute(
      AttributeDefinition ad){
    return setAttribute(ad, false);
  }
  public Attribute setAttribute(
      AttributeDefinition ad, boolean inner) {
    if(ad == null)
      return null;
    if(node.getTreeDB()!=null/*db is not inited*/ && !node.getTreeDB().getTreeLogic().changeAllowed(node, ad.getKey(), ad, inner))
      throw new OperationNotAllowedException("Cannot set "+ad.getKey()+" for node "+node.getUserVisibleName());
    if (!correctNested(ad)) 
      return null;
    if(!inner && (ad.isReadOnly()&&!ad.changeReadOnly()))
      throw new ReadOnlyAttributeException(ad.getKey());
    Attribute a = getAttribute(ad.getKey());
    setAttributeDef(ad);
    return a;
  }
  public Attribute forceSetAttribute(String name){
    AttributeDefinition ad = getWorkAttributeDefinition(name);
    if(ad!=null){
      Attribute a = ad.toAttribute(this);
      if(a!=null)
        return setAttribute(a);
      else
        return actualRemoveFromAttributeList(name);
    }
    return null;
  }
  public Attribute setAttribute(
      Attribute a) {
    try{
      writeLock();
      return putToAttributes(a);
    }
    finally{
      writeUnlock();
    }
  }

  private AttributeDefinition setAttributeDef(AttributeDefinition ad) {
    //Attribute a = new Attribute(v, ad);
    if(ad.getType() == AttributeType.REFERENCE || ad.getType()
        == AttributeType.LIST && ad.getRawValue()!=null && ((ListValue)ad.getRawValue().getValue()).getType()==AttributeType.REFERENCE)
      if(!(ad.getValueProvider() instanceof ReferenceValueProvider))
        throw new RuntimeException("Reference value has incorrect type");
    AttributeDefinition b = null;
    WriteLock wl = arwl.writeLock();
    try{
      wl.lock();
      b = putToAttributes(ad);
    } catch (OrderWasAlreadySetException e) {
      RLogger.logError(e);
    }
    finally{
      wl.unlock();
    }
    return b;
  }
  private AttributeDefinition putToAttributes(AttributeDefinition adef) throws OrderWasAlreadySetException {
    return putToAttributes(adef, false);
  }
  private AttributeDefinition putToAttributes(AttributeDefinition adef,boolean force) throws OrderWasAlreadySetException {
    String key = adef.getKey();
    AttributeDefinition b = getWorkAttributeDefinition(key);
    if (force || (b == null) ||
        b.getValueProvider()!=null && !b.getValueProvider().equals(adef.getValueProvider()) || 
        !b.getType().equals(adef.getType())||
        !b.getAvailability().equals(adef.getAvailability())||
        !b.getOrigin().equals(adef.getOrigin())) {
      changes.addSomeChange(key, b, adef);
    } 
    return b;
  }
  private Attribute putToAttributes(Attribute attribute) {
    boolean ch = true;
    Attribute b = attributes.get(attribute.getKey());
    TreeNode node = getNode();
    if (isAttributeDiffenent(attribute, b)) {
      attributes.put(attribute.getKey(), attribute);
    //if ((b == null) || !b.getValue().equals(attribute.getValue())) {
      if (b != null){
        ch = false;
        if(!b.getDefinition().equals(attribute.getDefinition()))
          ch = true;
        if(ch&&node.getUUId().equals(b.getOrigin()))
          unregisterVar(b);
      }
      if(ch){
        if(attribute.getOrigin().equals(node.getUUId()))
          registerVar(attribute.getDefinition());
        if(b!=null)
          b.getDefinition().getValueProvider().detach(node);
        attribute.getDefinition().getValueProvider().attach(node);
      }
    } 
    return b;
  }

  public static boolean isAttributeDiffenent(Attribute attribute, Attribute b) {
    if(attribute == null)
      return b != null;
    return (b == null) ||
        b.getValue() == null && attribute.getValue() != null ||
        b.getValue()!=null && !b.getValue().equals(attribute.getValue()) || 
        !b.getType().equals(attribute.getType())||
        !b.getAvailability().equals(attribute.getAvailability())||
        !b.getOrigin().equals(attribute.getOrigin());
  }
  public Attribute setConstAttribute(String key, AttributeGenerator ag, int flag) {
    AttributeType at = AttributeType.STRING;
    if(ag!=null)
      at = ag.getAttributeType();
    return setAttribute(new AttributeDefinition(getNode().getUUId(), 
        key, AttributeAvailability.LOCAL, new GeneratorValueProvider(ag),
        flag));
    
  }
  public Attribute setConstAttribute(
      AttributeType type, String key, Object val, int flag) {
    return setAttribute(new AttributeDefinition(getNode().getUUId(), 
        key, AttributeAvailability.LOCAL, new ConstValueProvider(type, val),
        flag));
  }
  public Attribute setConstAttribute(String key, EnumDefinitionValue listValue,int flag) {
    return setAttribute(new AttributeDefinition(getNode().getUUId(), 
        key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.ENUM_DEFINITION, listValue),
        flag));
  }

  public Attribute setConstAttribute(String key, TreeNode node, int flag) {
    return setAttribute(new AttributeDefinition(getNode().getUUId(), 
        key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.TREENODE, node.getUUId()),
        //TODO treeNode?
        flag));
  }
  public Attribute setConstAttribute(String key, AppliedEnumValue listValue,int flag) {
    return setAttribute(new AttributeDefinition(getNode().getUUId(), 
        key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.ENUM, listValue),
        flag));
  }

  public Attribute setConstAttribute(String key, ListValue listValue,int flag) {
   return setAttribute(new AttributeDefinition(getNode().getUUId(), 
      key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.LIST, listValue),
      flag));
  }

  public Attribute setConstAttribute(String key, Double listValue,int flag) {
   return setAttribute(new AttributeDefinition(getNode().getUUId(), 
      key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.FLOAT, listValue),
      flag));
  }

  public Attribute setConstAttribute(String key, Float listValue,int flag) {
   return setAttribute(new AttributeDefinition(getNode().getUUId(), 
      key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.FLOAT, listValue),
      flag));
  }
  public Attribute setConstAttribute(String key, String listValue,int flag) {
    return setConstAttribute(key, listValue, flag, false);
  }
  public Attribute setConstAttribute(String key, String listValue,int flag, boolean inner) {
   return setAttribute(new AttributeDefinition(getNode().getUUId(), 
      key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.STRING, listValue),
      flag), inner);
  }

  public Attribute setConstAttribute(String key, Integer listValue,int flag) {
   return setAttribute(new AttributeDefinition(getNode().getUUId(), 
      key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.INT, listValue),
      flag));
  }

  public Attribute setConstAttribute(String key, Boolean listValue,int flag) {
   return setAttribute(new AttributeDefinition(getNode().getUUId(), 
      key, AttributeAvailability.LOCAL, new ConstValueProvider(AttributeType.BOOL, listValue),
      flag));
  }

  public Map<String, Object> getAttributesMap(Set<String> deps) {

    Map<String, Object> result = new HashMap<>();
    for (String s:deps)
      if (!result.containsKey(s)) {
        Attribute a = getAttribute(s);
        if(a!=null){
          a = getAttribute(a.getKey());
          result.put(a.getKey(), a.getJavaValue(getNode())); // TODO
        }
        // think
      }
    return result;
  }

  private Object[] listToObject(List<?> l) {
    Object[] o = new Object[l.size()];
    for (int i = 0; i < l.size(); i++) {
      o[i] = l.get(i);
      if (o[i] instanceof List) o[i] = listToObject((List<?>) o[i]);
    }
    return o;
  }

  private void createScope(ScriptContext context, Map<String, Object> attributes) {
    // context.
    // Scriptable topScope = ScriptRuntime.initStandardObjects(context,
    // null, true);
    // Scriptable scope = new NativeObject();
    // scope.setParentScope( null );
    // scope.setPrototype( topScope );
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      String attrName = entry.getKey();
      Object value = entry.getValue();
      // Scriptable sc = scope;
      if (value instanceof List) {
        value = listToObject((List<?>) value);
      } else if (!value.getClass().isArray()) {
        if(value instanceof IContextStringProvider)
          value = ((IContextStringProvider)value).toString(this);
        else
          value = value.toString();
      }
      else if (value.getClass().isArray()) {
        // sc = new NativeArray((Object[])value);
        // sc.setParentScope( scope );
        // sc.setPrototype( scope );
      }
      context.setAttribute(attrName, value, ScriptContext.ENGINE_SCOPE);
      // Object obj = context.getWrapFactory().wrap( context, sc, value,
      // value.getClass() );
      // scope.put( attrName, scope, obj );
    }

    // return scope;
  }

  public String evaluatePredicateString(String predicate,Set<String> deps) {
    if (predicate == null || predicate.length() == 0) return "";
    String contextResult = "";
    try {
      ScriptEngineManager factory = new ScriptEngineManager();
      ScriptEngine engine = CoreUtils.getJSEngine();
      ScriptContext context = engine.getContext();
      // context.setWrapFactory(wrapFactory);
      Map<String, Object> attrMap = this.getAttributesMap(deps);
      createScope(context, attrMap);
      // Scriptable scope = createScope(context, attrMap);
      // invalid return
      Object res = engine.eval(predicate, context);
      // if(res instanceof NativeJavaObject)
      // contextResult = ((NativeJavaObject)res).unwrap().toString();
      // else
      contextResult = res.toString();
    } catch (Exception e) {
      String s = e.toString();
      return s;
    } finally {
    }
    return contextResult;
  }

//  public Attribute getUpdatedAttribute(Attribute a) {
//    if (a == null) return null;
//
//    if (a.getRawType().equals(AttributeType.ENUM) && a.getRawValue() instanceof AppliedEnumValue) {
//      AppliedEnumValue aev = a.getGRawValue();
//      if (aev != null) {
//        Object rt = aev.getValue();
//        // if(lt!=AttributeType.LIST){
//        a.setNewValue(rt);
//        // }
//      }
//      return a;
//    }
//    if (a.getResultType().equals(AttributeType.LIST)
//        || a.getResultType().equals(AttributeType.ENUM_DEFINITION)
//        || a.getRawValue() == null) return a;
//    String val = a.getRawValue().toString(), valret = null; // TODO tostring?
//    valret = getNewValue(a, a.getRawValue().toString());
//    if (!val.equals(valret)) {
//      a.setNewValue(valret);
//      return a;
//    }
//    return a;
//  }

  public Object getListAttributeValue(String attrLocations, AttributeType tp) {
    List<Object> ol = getListAttributeValue(attrLocations);
    if (ol == null) return null;
    switch (tp) {
      case BOOL:
        {
          List<Boolean> b = new ArrayList<>();
          for (Object o : ol) b.add((Boolean) o);
          return b;
        }
      case REFERENCE:
      {
        List<ReferenceValue> b = new ArrayList<>();
        for (Object o : ol){
          if(o instanceof ReferenceValue)
            b.add((ReferenceValue) o);
          else
            b.add(ReferenceValue.fromString(o.toString(), attrLocations));
        }
        return b;
      }
      case STRING:
        {
          List<String> b = new ArrayList<>();
          for (Object o : ol) b.add(o.toString());
          return b;
        }
      case INT:
        {
          List<Integer> b = new ArrayList<>();
          for (Object o : ol) b.add((Integer) o);
          return b;
        }
      case FLOAT:
        {
          List<Float> b = new ArrayList<>();
          for (Object o : ol) b.add((Float) o);
          return b;
        }

      default:
        break;
    }
    return null;
  }

//  public Attribute rawStoreAttribute(Attribute a) {
//    // Attribute result = getLocalAttribute(a.getKey());//TODO right?
//    WriteLock rl = arwl.writeLock();
//    Attribute b = null;
//    try {
//      rl.lock();
//      b = attributes.put(a.getKey(), a);
//    } finally {
//      rl.unlock();
//    }
//    if ((b == null) || !b.equals(a)) {
//      if (b != null){
//        unregisterVar(b);
//        changes.addChange(a.getKey());
//      }else
//        changes.addCreate(a.getKey());
//      registerVar(a.getDefinition());
//    }
//    return b;
//    // return result;
//  }

  private void unregisterVar(Attribute b) {
    if (b == null
        || b.getAvailability() == AttributeAvailability.LOCAL
        || !b.getOrigin().equals(node.getUUId())) return;
    TreeDB rt = getNode().getTreeDB();
    if(rt.isVirtual())
      rt = Instantiator.getRootTree(rt);
    Set<UUID> or = rt.attrRootCache.get(b.getKey());
    if (or != null) or.remove(b.getOrigin());
    if (or == null || or.isEmpty()) rt.attrRootCache.remove(b.getKey());
    Set<String> was = rt.attrRootRevCache.get(b.getOrigin());
    if (was != null && was.contains(b.getKey())) was.remove(b.getKey());
    if (was == null || was.isEmpty()) rt.attrRootRevCache.remove(b.getOrigin());
  }

  private void registerVar(AttributeDefinition a) {
    if (a == null || a.getAvailability() == AttributeAvailability.LOCAL) return;
    TreeDB rt = getNode().getTreeDB();
    if(rt.isVirtual())
      rt = Instantiator.getRootTree(rt);
    Set<UUID> or = rt.attrRootCache.get(a.getKey());
    if (or == null){
      or = new HashSet<>();
      rt.attrRootCache.put(a.getKey(), or);
    }
    or.add(a.getOrigin());

    Set<String> ks = rt.attrRootRevCache.get(a.getOrigin());
    if (ks == null){
      ks = new HashSet<>();
      rt.attrRootRevCache.put(a.getOrigin(), ks);
    }
    ks.add(a.getKey());
  }

  public static List<Attribute> addToList(
      TreeDB db, Object as, List<Attribute> al, Attribute a, UUID where) {
    int p = 0;
    synchronized (as) {
      if (al == null || al.size() == 0) al = new LinkedList<>();
      Iterator<Attribute> it = al.iterator();
      Attribute ia = null;
      boolean stop = false;
      while (it.hasNext()) {
        ia = it.next();
        if (!ia.isMoreVisible(db, a, where, true)) stop = true;
        if (ia.getOrigin().equals(a.getOrigin())) {
          it.remove();
          if (!stop) p--;
        }
        if (!stop) p++;
      }
      al.add(p, a);
    }
    return al;
  }

  /**
   * Get value of attribute with given name.
   *
   * @param key name of attribute
   * @return value of attribute with given name
   */
  public Object getAttributeValue(String key) {
    return getAttributeValue(key, null);
  }

  /**
   * Get value of attribute with given key. If attribute with given key is not exists then return
   * value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public Object getAttributeValue(String key, Object def) {
    Attribute value = getAttribute(key);
    return value != null ? value.getGValue() : def;
  }

  /**
   * Get value of attribute with given key casted to TreeNode type. If attribute with given key is
   * not exists then return value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public List<TreeNode> getLinkAttributeValue(String key, TreeNode def) {
    Attribute a = getAttribute(key);
    if(a!=null && a.isReference())
      return ReferenceValue.getValues(this, a.getType(), a.getValue());
    if(def == null)
      return null;
    List<TreeNode> l = new LinkedList<>();
    l.add(def);
    return l;
  }
  public Map<UUID, String> getLinkAttributeStoredVersions(String key) {
    Attribute a = getAttribute(key);
    if(a!=null && a.isReference())
      return ReferenceValue.getLinkAttributeStoredVersions(this, a);
    return null;
  }

  public boolean isReference(String key) {
    Attribute a = getAttribute(key);
    if (a != null)
      return a.isReference();
    return false;
  }

  public List<TreeNode> getLinkAttributeValue(String key) {
    return getLinkAttributeValue(key, null);
  }

  /**
   * Get value of attribute with given key casted to int type. If attribute with given key is not
   * exists then return value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public int getIntAttributeValue(String key, int def) {
    Attribute value = getAttribute(key);
    return value != null ? value.getGValue() : def;
  }

  /**
   * Get value of attribute with given key casted to int type.
   *
   * @param key name of attribute
   * @return value of attribute with given name
   */
  public int getIntAttributeValue(String key) {
    return getIntAttributeValue(key, 0);
  }

  /**
   * Get value of attribute with given key casted to float type. If attribute with given key is not
   * exists then return value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public float getFloatAttributeValue(String key, float def) {
    Attribute value = getAttribute(key);
    return value != null ? value.getGValue() : def;
  }

  /**
   * Get value of attribute with given key casted to float type.
   *
   * @param key name of attribute
   * @return value of attribute with given name
   */
  public float getFloatAttributeValue(String key) {
    return getFloatAttributeValue(key, 0);
  }

  /**
   * Get value of attribute with given key casted to boolean type. If attribute with given key is
   * not exists then return value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public boolean getBooleanAttributeValue(String key, boolean def) {
    Attribute value = getAttribute(key);
    if (value != null) {
      Object val = value.getGValue();
      if (val instanceof Boolean) return (Boolean) val;
      if(val == null)
        return def;
      return Boolean.parseBoolean(val.toString());
    }
    return def;
    // return value != null ? (Boolean) value.getValue() : def;
  }

  /**
   * Get value of attribute with given key casted to boolean type.
   *
   * @param key name of attribute
   * @return value of attribute with given name
   */
  public boolean getBooleanAttributeValue(String key) {
    return getBooleanAttributeValue(key, false);
  }

  /**
   * Get value of attribute with given key casted to string type.
   *
   * @param key name of attribute
   * @return value of attribute with given name
   */
  public String getStringAttributeValue(String key) {
    return getStringAttributeValue(key, null);
  }

  public String getStringAttributeValue(String key, String def) {
    return getStringAttributeValue(key, def, 10);
    // return value != null ? (String) value.getValue() : def;
  }

  public TreeNode getTreeNodeValue(String key) {
	Attribute a = getAttribute(key);
	if(a == null)
      return null;
	TreeNode node = (TreeNode) a.getValue();
	return node;
  }

  /**
   * Get value of attribute with given key casted to string type. If attribute with given key is not
   * exists then return value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public String getStringAttributeValue(String key, String def, int depth) {
    Attribute value = getAttribute(key);
    if (value == null) return def;
//    if (depth > 0)
//      return getNewValue(
//          value,
//          Attribute.valueToString(value.getResultType(), value.getRawValue(this, false)),
//          depth - 1); // temporary
//    return value.getRawValue(this).toString();
    Object o = value.getGValue();
    if(o == null)
      return def;
    if(o instanceof ReferenceValue)
      return ((ReferenceValue)o).toString(this);
    return o.toString();
    // return value != null ? (String) value.getValue() : def;
  }

  /**
   * Get value of attribute with given key casted to string array type.
   *
   * @param key name of attribute
   * @return value of attribute with given name
   */
  public String[] getStringArrayAttributeValue(String key) {
    return getStringArrayAttributeValue(key, new String[0]);
  }

  public String[] getStringArrayAttributeValue(String key, String[] def) {
    return getStringArrayAttributeValue(key, def, 10);
    // return value != null ? (String) value.getValue() : def;
  }

  /**
   * Get value of attribute with given key casted to string type. If attribute with given key is not
   * exists then return value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public String[] getStringArrayAttributeValue(String key, String[] def, int depth) {
    Attribute value = getAttribute(key);
    if (value == null) return def;
    return toStringArray(this, value, def);
    // return value != null ? (String) value.getValue() : def;
  }

  /**
   * Get value of attribute with given key casted to list of string.
   *
   * @param key name of attribute
   * @return value of attribute or "def" if it is not found
   */
  public List<Object> getListAttributeValue(String key) {
    ListValue lv = ((ListValue) getAttributeValue(key, null));
    if(lv == null)
      return null;
    return lv.getValues();//TODO convert
  }

//  public Set<String> keySet() {
//    TreeNode cp = getNode().getCloneParent();
//    ReadLock rl = arwl.readLock();
//    Set<String> keys;
//    try {
//      rl.lock();
//      keys = new HashSet<String>(attributes.keySet());
//    } finally {
//      rl.unlock();
//    }
//
//    if (cp != null) keys.addAll(cp.attrs().keySet());
//    Iterator<String> ks = keys.iterator();
//    while (ks.hasNext()) if (ks.next().startsWith("_%")) ks.remove();
//    // if(node.isVirtual()&&db.isVirtual()){
//    // TreeDB db = VirtualNode.getRootTree(node.getTreeDB());
//    // if(db!=null&&!db.isVirtual()){
//    // TreeNode cn = db.getCachedNode(node.getUUId());
//    // keys.addAll(cn.attrs().keySet());
//    // }
//    // }
//    return keys;
//  }

  public boolean containsKey(String s) {
    return attributes.containsKey(s);
  }

  @Override
  public Iterator<Attribute> iterator() {
    List<Attribute> list = new LinkedList<>();
    Set<String> keys = getAllAttributeKeys();
    for (String s : keys) {
      Attribute a = attributes.get(s);
      //Attribute a = getAttribute(s);
      if (a != null) list.add(a);
    }
    return list.iterator();
  }

  public Map<String, Attribute> getAvailableAttributes() {
    Attribute ida = null;
    // Attribute lida = null;
    Map<String, Attribute> ret = new HashMap<>();
    ReadLock l = arwl.readLock();
    try {
      l.lock();
      if (attributes == null) {
        return null;
      }
      ret.putAll(attributes);
    } finally {
      l.unlock();
    }
    for (String s : ValueProviderFactory.getVirtAttrsNames()) {
      ida = null;
      Attribute a = getAttribute(s);
      if (a != null) ida = a;
      // ida.add(getAttribute("_%id"));
      // if(attributes.containsKey(s)){
      // .if(ida.isMoreVisible(attributes.get(s), node.getUUId(),true))
      // ret.put(s, ida);
      // //ida.addAll(lida);
      // //attributes.put(s, ida);
      // }else
      ret.put(s, ida); // REWRITE for all autogenerated
      // synchronized (attributes) {
      // attributes.put(s, ida);
      // }
    }
    return ret;
  }

  public Attribute getLocalAttribute(String key) {
    ReadLock rl = arwl.readLock();
    try {
      rl.lock();
      return attributes.get(key);
    } finally {
      rl.unlock();
    }
  }

  public Map<String, Attribute> getActiveAttributes() {
    return attributes;
  }

  public Attribute[] toArray() {
    return getLocalAttributes().values().toArray(new Attribute[0]);
//    Set<String> keys = getLocalAttributeKeys();
//    Attribute[] attrs = new Attribute[keys.size()];
//    int i = 0;
//    for (String s : keys) {
//      Attribute a = getAttribute(s);
//      if (a != null) attrs[i++] = a;
//    }
//    if (i <= keys.size()) attrs = Arrays.copyOf(attrs, i);
//    return attrs;
  }

  public static Map<String, AttributeDefinition> formTypeMap(UUID newuid, String typeName) {
    Map<String, AttributeDefinition> map = new HashMap<>();
    map.put(
        TreeNode.ATTR_TYPE,
        new AttributeDefinition(newuid, 
                                    TreeNode.ATTR_TYPE,
                                    AttributeAvailability.LOCAL, 
                                    new ConstValueProvider(AttributeType.STRING, typeName), 
                                    0));
    return map;
  }
  
  private static String[] tarian(Set<String> keySet, Map<String, Set<String>> revDep) {
    Map<String, Byte> color = new HashMap<>();
    Iterator<String> it = keySet.iterator();
    LinkedList<String> work = new LinkedList<>();
    LinkedList<String> ret = new LinkedList<>();
    while(it.hasNext()){
      String next = it.next();
      if(color.get(next)==0){
        work.add(next);
        while(!work.isEmpty()){
          String s = work.peekLast();
          color.put(s, (byte)1);
          for(String s2: revDep.get(s)){
            if(color.get(s2)==1)
              throw new RuntimeException("");
            if(color.get(s2)==0){
              work.addLast(s2);
              continue;
            }
          }
          color.put(s, (byte)2);
          ret.addFirst(s);
          work.pollLast();
        }
      }
    }
    return ret.toArray(new String[0]);
  }

  public static Collection<Attribute> toCollection(Map<String, Attribute> attributesInList) {
    LinkedList<Attribute> list = new LinkedList<>(attributesInList.values());
    list.remove(null);
    return list;
  }
  public Set<String> getDependencies(String target) {
    Set<String> rev = Collections.emptySet();
    //TODO test

    AttributeDefinition a = changes.getNew(target);
    if(a == null && ValueProviderFactory.getVirtAttrsNames().contains(target)){//avoid generate virt labels when do not needed
      AttributeDefinition def = getNode().getVirtualAttributeDefinition(target);
      if(def!=null){
        rev = getNode().getVirtualAttributeDefinition(target).getDependencies(getNode().attrs());
        if(rev!=null)
          return rev;
        return Collections.emptySet();
      }
    }
    if (a != null) {
      rev = a.getDependencies(this);
    }else{
      Attribute b = getAttribute(target);
      if(b!=null)
        rev = b.getDefinition().getDependencies(this);
    }
    if(rev!=null)
      return rev;
    return Collections.emptySet();
  }

  public boolean getLocalBooleanAttributeValue(String key, boolean b) {
    Attribute value = getAttribute(key);
    if (value != null) {
      TypedValue tv = value.getTValue();
      if(tv!=null && tv.getValue()!=null){
        Object o = tv.getValue();
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(o.toString());
      }
    }
    return b;
  }

  /**
   * Get value of attribute with given key casted to string type. If attribute with given key is not
   * exists then return value of "def"
   *
   * @param key name of attribute
   * @param def default return value if attribute is not found
   * @return value of attribute or "def" if it is not found
   */
  public String getLocalStringAttributeValue(String key, String def) {
    Attribute value = getLocalAttribute(key);
    if (value == null) return def;
    TypedValue tv = value.getTValue();
    if(tv!=null && tv.getValue()!=null)
      return tv.getValue().toString();
    return def; 
//    return getNewValue(
//        value,
//        Attribute.valueToString(value.getResultType(), value.getNewValue(this))); // temporary
    // return value != null ? (String) value.getValue() : def;
  }

  public String getLocalStringAttributeValue(String key) {
    return getLocalStringAttributeValue(key, null);
  }

  public static boolean attributesEquals(
      Map<String, Attribute> attributes2, Map<String, Attribute> attributes3) {
    if (attributes2.keySet().size() != (attributes3.keySet().size())) return false;
    for (String s : attributes2.keySet()) {
      Attribute a = attributes2.get(s), b = attributes3.get(s);
      if (a == null) return b == null;
      else return b != null && a.equals(b);
    }
    return true;
  }

  public static AttributeStorage getCachedStorage(TreeDB db, UUID cpuid) {
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    return db.getCache().containsKey(cpuid) ? db.getCache().get(cpuid).attrs() : null;
  }
  public static AttributeDefinition mergeAttrDefs(TreeDB db, UUID where, AttributeDefToMerge... lists) {
    AttributeDefinition res = null;
    for (AttributeDefToMerge a : lists)
      if (a.attr != null
          && (a.attr.getAvailability() != AttributeAvailability.LOCAL
              || a.param != MergeParams.NOTLOCAL)
          && (a.param == MergeParams.ALLHP
              || res == null
              || a.attr.isMoreVisible(db, res, where, true)))
        res = a.attr;
    return res;
  }
  public static Attribute mergeAttrs(TreeDB db, UUID where, AttributeToMerge... lists) {
    Attribute res = null;
    for (AttributeToMerge a : lists)
      if (a.attr != null
          && (a.attr.getAvailability() != AttributeAvailability.LOCAL
              || a.param != MergeParams.NOTLOCAL)
          && (a.param == MergeParams.ALLHP
              || res == null
              || a.attr.isMoreVisible(db, res, where, true)))
        res = a.attr;
    return res;
  }
  public TreeNode getNode() {
    return node;
    // return node;
  }
  public void setNode(TreeNode node) {
    this.node = node;
  }
  
  public boolean hasChanges() {
    if (changes.isEmpty()) return false;
    return true;
  }
  public static Object getAttributeDefValue(
      TreeDB db, Map<String, AttributeDefinition> attrs, UUID where, String key) {
    AttributeDefinition a = getAD(db, attrs, where, key);
    if (a != null && a.getRawValue() != null) 
        return a.getRawValue().getValue();
    return null;
  }
  public static Object getAttributeValue(
      TreeDB db, Map<String, Attribute> attrs, UUID where, String key) {
    Attribute a = getAttribute(db, attrs, where, key);
    if (a != null) 
        return a.getGValue();
    return null;
  }

  public static Attribute getAttribute(
      TreeDB db, Map<String, Attribute> attrs, UUID where, String key) {
    if (attrs == null) return null;
    Attribute a = attrs.get(key);
    if (a != null) {
      // Attribute a = list.iterator().next();
      if (!a.getOrigin().equals(where)) {
        AttributeStorage as = AttributeStorage.getCachedStorage(db, a.getOrigin());
        if (as != null) return as.getAttribute(key);
        else {
          TreeNode node = db.getNode(a.getOrigin());
          if (node != null) return node.attrs().getAttribute(key);
          attrs.remove(key);
          // TODO delete from as?
          return null;
        }
      } else return a;
    }
    return null;
  }
  public static AttributeDefinition getAD(
      TreeDB db, Map<String, AttributeDefinition> attrs, UUID where, String key) {
    if (attrs == null) return null;
    AttributeDefinition a = attrs.get(key);
    if (a != null) {
      // Attribute a = list.iterator().next();
      if (!a.getOrigin().equals(where)) {
        AttributeStorage as = AttributeStorage.getCachedStorage(db, a.getOrigin());
        if (as != null){
          Attribute ab = as.getAttribute(key);
          if(ab == null)
            return null;
          return ab.getDefinition();
        }
        else {
          TreeNode node = db.getNode(a.getOrigin());
          if (node != null){
            Attribute ab = node.attrs().getAttribute(key);
            if(ab!=null)
              return ab.getDefinition();
            return null;
          }
          // VSCODE-PATCH: the origin node is not in the catalog (old format copies): keep the stored value. The removal
          // changed the attributes cached by the storage, so a node lost its _type and the format conversion failed.
          return a;
        }
      } else return a;
    }
    return null;
  }
  public static void setAttribute(Map<String, Attribute> na, Attribute attribute) {
    na.put(attribute.getKey(), attribute);
  }

  public boolean hasVisibleChanges() {
    for (String s : changes.getAll()) {
      Attribute a = getAttribute(s);//TODO think
      if(a!=null && a.isUserVisible())
        return true;
      if(a==null && !s.startsWith("_")&&!ValueProviderFactory.isVirtName(getNode(),s))
        return true;
    }
    return false;
  }

  public List<TreeNode> getLinkAttributeValues(String s) {
    return getLinkAttributeValue(s);
  }

  public VersionsStorage getVersionsStorage() {
    return vs;
  }
  public static List<String> formList(String ... defReferenceName) {
    return Arrays.asList(defReferenceName);
  }

  public static Set<String> formSet(String...attrs) {
    Set<String> ch = new HashSet<>();
    ch.addAll(Arrays.asList(attrs));
    return ch;
  }
  public static Map<String, Attribute> formAttributeMap(Attribute ... attrs) {
    Map<String,Attribute> nmap = new TreeMap<>();
    for(Attribute a:attrs)
      if(a!=null)
        nmap.put(a.getKey(), a);
    return nmap;
  }
  public static Map<String, AttributeDefinition> formAttributeDefMap(Attribute... attrs) {
    Map<String,AttributeDefinition> nmap = new TreeMap<>();
    for(Attribute a:attrs)
      if(a!=null)
        nmap.put(a.getKey(), a.getDefinition());
    return nmap;
  }

  public static Map<String, AttributeDefinition> formAttributeDefMap(AttributeDefinition... attrs) {
    Map<String,AttributeDefinition> nmap = new TreeMap<>();
    for(AttributeDefinition a:attrs)
      nmap.put(a.getKey(), a);
    return nmap;
  }

  public static boolean attributesDefsEquals(
      Map<String, AttributeDefinition> attributes2, Map<String, AttributeDefinition> attributes3) {
    Set<String> notwas = new HashSet<>(attributes3.keySet());
    for(String s:attributes2.keySet()){
      if(!notwas.contains(s))
        return false;
      notwas.remove(s);
      AttributeDefinition ad1 = attributes2.get(s),
                          ad2 = attributes3.get(s);
      if(ad1!=ad2)
        return false;
    }
    return notwas.isEmpty();
  }

  public AttributeDefinition createAttributeDescription(
      String key, IValueProvider vp) {
    return new AttributeDefinition(getNode().getUUId(), key, vp);
  }

  public static String getNodeRawTypeByA(UUID uuid, Map<String, Attribute> attributes1) {
    if (attributes1 == null) return null;
    AttributeDefinition alist = attributes1.get(TreeNode.ATTR_TYPE).getDefinition();
    if (alist == null || alist.getRawValue() == null) return null;
    String type = (String) alist.getRawValue().getValue();
    return type;
  }
  public static String getNodeRawTypeByAD(UUID uuid, Map<String, AttributeDefinition> attributes2) {
    if (attributes2 == null) return null;
    AttributeDefinition alist = attributes2.get(TreeNode.ATTR_TYPE);
    if (alist == null || alist.getRawValue() == null) return null;
    String type = (String) alist.getRawValue().getValue();
    return type;
  }

  public Map<String,AttributeDefinition> getLocalDMap() {
    Map<String,AttributeDefinition> map = new HashMap<>();
    for(String s:getLocalAttributeKeys())
    {
      Attribute a = getAttribute(s);
      if(a!=null)
        map.put(s, a.getDefinition());
    }
    return map;
  }

  public Map<String, AttributeDefinition> getPropogatingAttributeDefs() {
    Map<String,AttributeDefinition> ad = new HashMap<>();
    Map<String,Attribute> am = getActiveAttributes();
    Set<String> lates = null;
    for(Entry<String, Attribute> en:am.entrySet()){
      if(en.getValue()!=null){
        AttributeAvailability av = en.getValue().getAvailability();
        if(av == AttributeAvailability.SUBTREE ||
          av==AttributeAvailability.DIRECT_CHILDREN && en.getValue().getOrigin().equals(getNode().getUUId()))
            ad.put(en.getKey(), en.getValue().getDefinition());
        else{
          Set<UUID> nodes = getNode().getTreeDB().attrRootCache.get(en.getKey());
          if(nodes == null || nodes.isEmpty())
            continue;
          if(lates==null)
            lates = new HashSet<>();
          lates.add(en.getKey());
        }
      }
    }
    if(lates!=null){
      //TreeNode node = getNode();
      Set<TreeNode> washere = new HashSet<>();
      LinkedList<TreeNode> toh = new LinkedList<>();
      Map<TreeNode,Integer> deep = new HashMap<>();
      Set<String> tor = new HashSet<>();
      deep.put(node, 0);
      toh.add(node);
      while(!toh.isEmpty()&&!lates.isEmpty()){
        TreeNode node = toh.remove(0);
        Integer dp = deep.get(node);
        tor.clear();
        for(String s:lates)
        {
          Attribute a = node.attrs().getAttribute(s);
          if(a!=null){
            AttributeAvailability av = a.getAvailability();
            if(av == AttributeAvailability.DIRECT_CHILDREN){
              if(dp == 0 && a.getOrigin().equals(node.getUUId())){
                ad.put(s, a.getDefinition());
                tor.add(s);
              }
            }else
              if(av == AttributeAvailability.SUBTREE){
                ad.put(s, a.getDefinition());
                tor.add(s);
              }
          }
        }
        for(String s:tor)
          lates.remove(s);
        TreeNode cp = node.getCloneParent();
        if(cp!=null&&!washere.contains(cp)){
          toh.add(cp);
          deep.put(cp, dp);
        }
        TreeNode par = node.getWorkParent();
        if(par!=null && !washere.contains(par)){
          toh.add(par);
          deep.put(par, dp+1);
        }
      }
    }
    return ad;
  }
  public Map<String, AttributeDefinition> getActiveAttributeDefs() {
    Map<String,AttributeDefinition> ad = new HashMap<>();
    Map<String,Attribute> am = getActiveAttributes();
    for(String s:am.keySet())
      if(am.get(s)!=null)
        ad.put(s, am.get(s).getDefinition());
    return ad;
  }
  public Map<String, AttributeDefinition> getAvailableAttributeDefs() {
    Map<String,AttributeDefinition> ad = new HashMap<>();
    Map<String,Attribute> am = getAvailableAttributes();
    for(String s:am.keySet())
      if(am.get(s)!=null)
        ad.put(s, am.get(s).getDefinition());
    return ad;
  }

  public Map<String, AttributeDefinition> getRawDMap() {
    Map<String,AttributeDefinition> adm = new HashMap<>();
    for(String s:attributes.keySet())
      adm.put(s, attributes.get(s).getDefinition());
    return adm;
  }

  public static Map<String, AttributeDefinition> toDefMap(Map<String, Attribute> attributes2) {
    Map<String,AttributeDefinition> ad = new HashMap<>();
    for(String s:attributes2.keySet())
      if(attributes2.get(s)!=null){
        AttributeDefinition ada = attributes2.get(s).getDefinition();
        if(ada.isVirtual())
          continue;
        ad.put(s, ada);
      }else 
        ad.put(s, null);
    return ad;
  }

  public ChangesSet<AttributeDefinition> getChanged() {
    return changes;
  }
  public void setChanged(ChangesSet<AttributeDefinition> changesSet) {
    changes = changesSet;
  }

  public static void updateMap(
      Map<String, AttributeDefinition> map,
      ChangesSet<Attribute> changes2) {
    Map<String, Attribute> newA = changes2.getNewMap();
    for(Entry<String, Attribute> en:newA.entrySet()){
      Attribute a = en.getValue();
      if(a == null)
        map.remove(en.getKey());
      else
        map.put(en.getKey(),en.getValue().getDefinition());
    }
  }

  public void clearChanges() {
    changes.clear();
  }

  public static Set<String> formPredDeps(String predicate, AttributeStorage as) {
   
      String formula = predicate;
      if(formula == null)
        return new HashSet<>();
      Set<String> dep = new HashSet<>();
      for(String s:as.getAllAttributeKeys()){
        if(formula.indexOf(s)>-1)
          dep.add(s);
      }
      return dep;
  }
//tends to be called only on one position
  public boolean updateAttribute(String key) {
    if(getNode().isDisposed())
      return false;
    Attribute a = getLocalAttribute(key);
    if(a != null){
      Object nw = a.getDefinition().getValue(this);
      Object aval = a.getValue(); 
      if(aval!=null && !aval.equals(nw) ||
          aval == null && nw != null)
      {
        AttributeDefinition c = getWorkAttributeDefinition(key);
        try {
          putToAttributes(a.getDefinition(),true);
          return c!=a.getDefinition();
        } catch (OrderWasAlreadySetException e) {
          RLogger.logError(e);
        }
      }
    }else{
      AttributeDefinition ad = getNode().getVirtualAttributeDefinition(key);
      if(ad!=null)
      {
        try {
          putToAttributes(ad, true);
          return true;
        } catch (OrderWasAlreadySetException e) {
          RLogger.logError(e);
        }
      }
    }
    return false;
  }

  public AttributeDefinition getAttributeDefinition(String s) {
    if(attributes.containsKey(s))
    {
      Attribute a = attributes.get(s);
      if(a != null)
        return a.getDefinition();
    }
    return node.getVirtualAttributeDefinition(s);
  }

  public void commitChanges(ChangesSet<Attribute> cs, boolean save, byte flag) {
    getNode().getTreeDB().commitAttributes(getNode(), cs, save, false, flag);
  }

  public Attribute removeAttributeCommit(String cs) {
    return attributes.remove(cs);
  }
  public static String[] toStringArray(AttributeStorage as, Attribute new1, String[] defvalue) {
    TypedValue tv = new1.getTValue();
    if(tv == null || tv.getValue() == null) return defvalue;
     
    if (tv.getType().equals(AttributeType.LIST)) {
      ListValue lv = tv.getGValue();
      //List<Object> lv = (List<Object>) value.getNewValue(this);
      String[] res = new String[lv.getValues().size()];
      int i = 0;
      for (Object o : lv) {
        String val = Attribute.valueToString(as, lv.getType(), o);
//        if (depth > 0) res[i++] = lv.getValues().iterator().next()
        if(val.trim().length()>0)
          res[i++] = val;
      }
      if(i<res.length)
        return Arrays.copyOf(res, i);
      return res;

    } else {
//      if (depth > 0) {
//        res[0] = tv.getGValue().toString();
////            getNewValue(
////                value,
////                Attribute.valueToString(value.getResultType(), value.getNewValue(this)),
////                depth - 1); // temporary
//        return res;
//      }

      String val = Attribute.valueToString(as, tv.getType(),tv.getValue());
      String[] res = null;
      if(val.trim().length()>0) {
        res = new String[1]; 
        res[0] = Attribute.valueToString(as, tv.getType(),tv.getValue());
      }else 
        res = new String[0];
      return res;
    }
  }
  public String[] toStringArray(Attribute new1, String[] def) {
    return toStringArray(this, new1, def);
  }
  public Map<String, Object> getAttributeValues() {
    Map<String, Object> result = new HashMap<>();
    for (Entry<String, Attribute> en : getLocalAttributes().entrySet()) 
      result.put(en.getKey(), en.getValue().getValue());
    return result;
  }
}
