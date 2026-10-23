package com.unitesk.requality.core.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IStoppable;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.reference.IReferenceVersionRule;
import com.unitesk.requality.core.attribute.reference.TreeRelationsCounter;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheManipulator;
import com.unitesk.requality.core.storage.cache.CacheManipulatorInitializationStage;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.cache.RelationRecord;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
import com.unitesk.requality.core.storage.cache.TermsCacheMan;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class TermsManipulator extends CacheManipulator<TreeDB> implements IStoppable {
  private static String NAME = "Terms Manipulator";
  public static final String TERMS_INITED = "terms-inited";
  public static final String DEF_TERM_ATTR_NAME = "def-term", USE_TERM_ATTR_NAME = "use-term",
      USE_TERM_ALT_ATTR_NAME = "usedef", TERM_REF_ATTR_PREFIX = "term-";
  public static final String SEPARATOR = "::", VERSION_SEPARATOR = "|v";
  private TreeDB db = null;
  private IResTracker rt = null;
  private TermsCacheMan tcm = null;
  private boolean isCP = false;
  private ITreeChangeListener lis = new DefaultTreeChangeListener() {
    @Override
    public void changed(TreeNode node, ChangesSet<Attribute> what) {
      if (what.contains(USE_TERM_ATTR_NAME))
        addRemoveUse(node, what.getOld(USE_TERM_ATTR_NAME), what.getNew(USE_TERM_ATTR_NAME), isCP);
      if (what.contains(USE_TERM_ALT_ATTR_NAME))
        addRemoveUse(node, what.getOld(USE_TERM_ALT_ATTR_NAME), what.getNew(USE_TERM_ALT_ATTR_NAME), isCP);
      if (what.contains(DEF_TERM_ATTR_NAME))
        addRemoveDef(node, what.getOld(DEF_TERM_ATTR_NAME), what.getNew(DEF_TERM_ATTR_NAME), isCP);
    }

    @Override
    public void created(TreeNode node) {
      createdActions(node);
    }

    @Override
    public void deleted(TreeNode node) {
      String[] use1 = node.attrs().getStringArrayAttributeValue(USE_TERM_ATTR_NAME);
      String[] use2 = node.attrs().getStringArrayAttributeValue(USE_TERM_ALT_ATTR_NAME);
      String[] defterm = node.attrs().getStringArrayAttributeValue(DEF_TERM_ATTR_NAME);
      if (use1 != null && use1.length > 0 || use2 != null && use2.length > 0)
        removeUseTerm(node, isCP);
      if (defterm != null && defterm.length > 0)
        removeDefTerm(node, isCP);
    }
  };

  @Override
  public CacheManipulatorInitializationStage getCacheState(SettingsManipulator sman, TreeDB treeDB,
      CacheInitializationStage stage) {
    if (stage == CacheInitializationStage.FULL || stage == CacheInitializationStage.INIT)
      return super.getCacheState(sman, treeDB, stage);
    return CacheManipulatorInitializationStage.NON_APPLIABLE;
  }

  private boolean stopped = false;

  private void createdActions(TreeNode node) {
    // TODO Auto-generated method stub
    String[] use1 = node.attrs().getStringArrayAttributeValue(USE_TERM_ATTR_NAME);
    String[] use2 = node.attrs().getStringArrayAttributeValue(USE_TERM_ALT_ATTR_NAME);
    String[] defterm = node.attrs().getStringArrayAttributeValue(DEF_TERM_ATTR_NAME);
    if (use1 != null && use1.length > 0)
      setUseTerm(node, null, use1, true, isCP);
    if (use2 != null && use2.length > 0)
      setUseTerm(node, null, use2, true, isCP);
    if (defterm != null && defterm.length > 0)
      setDefTerm(node, null, defterm, true, isCP);
  }

  private static String getTreeId(TreeDB db) {
    if (db == null)
      return null;
    RevisionInfo ri = TreeStateManager.getOne(db.getTracker()).getRevisionInfo(db);
    if (ri != null)
      return db.getProjectName() + ri.getRevision();
    return db.getProjectName();
  }

  private static TermsManipulator getCPTermsStorage(TreeDB dba) {
    return dba.getTracker().getCMM(dba).getTermsManipulator();
  }

  public TermsCacheMan getTCM() {
    if (tcm == null)
      if (db != null)
        tcm = new TermsCacheMan(db, false);
      else
        tcm = new TermsCacheMan(rt, true);
    return tcm;
  }

  public TermsManipulator(IResTracker rt) {
    this.rt = rt;
    this.db = null;
    isCP = true;
  }

  public TermsManipulator(TreeDB db) {
    this.db = db;
    this.rt = db.getTracker();
    isCP = false;
  }
//  public boolean isInited(){
//    //cs.getCacheValue(db.getRootNode().toString(), "", key)
//  }

  public void initCache(TreeNode node) {
    addRemoveUse(node, null, node.attrs().getAttribute(USE_TERM_ATTR_NAME), isCP);
    addRemoveUse(node, null, node.attrs().getAttribute(USE_TERM_ALT_ATTR_NAME), isCP);
    addRemoveDef(node, null, node.attrs().getAttribute(DEF_TERM_ATTR_NAME), isCP);
  }

  private static void addRemoveDef(TreeNode node, Attribute old, Attribute new1, boolean isCP) {
    boolean oldIsLocal = old != null && old.getOrigin().equals(node.getUUId());
    boolean newIsLocal = new1 != null && new1.getOrigin().equals(node.getUUId());
    if (old != null || new1 != null) {
      if (old != null) {
        if (new1 == null) {
          if (oldIsLocal)
            removeDefTerm(node, isCP);
        } else {
          if (newIsLocal) {
            String[] tadd = node.attrs().toStringArray(new1, null);
            String[] told = old == null ? null : node.attrs().toStringArray(old, null);
            setDefTerm(node, told, tadd, false, isCP);
          }
        }
      } else if (new1 != null && newIsLocal) {
        String[] tadd = node.attrs().toStringArray(new1, null);
        setDefTerm(node, null, tadd, false, isCP);
      }
    }
  }

  private static void addRemoveUse(TreeNode node, Attribute old, Attribute new1, boolean isCP) {
    if (old != null || new1 != null) {
      if (old != null) {
        if (new1 == null)
          removeUseTerm(node, isCP);
        else {
          setUseTerm(node, node.attrs().toStringArray(old, null), node.attrs().toStringArray(new1, null),
              false, isCP);
        }
      } else if (new1 != null)
        setUseTerm(node, null, node.attrs().toStringArray(new1, null), false, isCP);
    }
  }

  private static void setUseTerm(TreeNode node, String[] useold, String[] use2, boolean init, boolean isCP) {
    if ((use2 == null || use2.length == 0) && (useold == null || useold.length == 0))
      return;
    Map<String, String> vers = null, oldverss = null;
    IReferenceVersionRule rule = node.getTreeDB().getAttributeRules().getReferenceVersionRule();
    Set<String> test = null;
    Set<String> test2 = null;
    if (use2 != null)
      for (int i = 0; i < use2.length; i++) {
        String s = use2[i];
        int ind = s.indexOf(VERSION_SEPARATOR);
        if (ind > -1) {
          String versa = s.substring(ind + VERSION_SEPARATOR.length());
          s = s.substring(0, ind);
          if (vers == null)
            vers = new HashMap<>();
          vers.put(s, versa);
          use2[i] = s;
        }
        if (rule != null && (rule.checkFrom(node, filtered(s)))) {
          if (test == null)
            test = new HashSet<String>();
          test.add(s);
        }
      }
    if (useold != null)
      for (int i = 0; i < useold.length; i++) {
        String s = useold[i];
        int ind = s.indexOf(VERSION_SEPARATOR);
        if (ind > -1) {
          String versa = s.substring(ind + VERSION_SEPARATOR.length());
          s = s.substring(0, ind);
          if (oldverss == null)
            oldverss = new HashMap<>();
          oldverss.put(s, versa);
          useold[i] = s;
        }
        if (rule != null && (rule.checkFrom(node, filtered(s)))) {
          if (test2 == null)
            test2 = new HashSet<String>();
          test2.add(s);
        }
      }

    if (!isCP)
      node.getTreeDB().getTermsStorage().getTCM().setUseTerm(node.getTreeDB(), node.getSUUId(),
          node.getTreeDB().getProjectName(), useold, oldverss, use2, vers);
    Map<String, List<String>> cpterms = filteruseterm(use2, vers);
    Map<String, List<String>> cptermsold = filteruseterm(useold, oldverss);
    if (isCP)
      getCPTermsStorage(node.getTreeDB()).getTCM().setCPUseTerm(node, cptermsold, oldverss, cpterms, vers);

    if ((test != null && test.size() > 0 || test2 != null && test2.size() > 0) && !init) {
      Map<String, Map<TreeNode, String>> map = getUseToDefTermsRelations(node, null);
      for (String s : map.keySet()) {
        if (test != null && test.contains(s)) {
          Map<TreeNode, String> versa = map.get(s);
          TreeNode defnode = versa.keySet().iterator().next();
          if (versa.get(defnode) == null && rule != null && rule.checkTo(node, defnode, filtered(s))) {
            if (vers == null)
              vers = new HashMap<>();
            vers.put(s, defnode.getNodeVersion());
          }
        } else if (test2 != null && test2.contains(s)) {
          // TOREMOVE
          Map<TreeNode, String> versa = map.get(s);
          TreeNode defnode = versa.keySet().iterator().next();
          if (versa.get(defnode) == null && rule != null && rule.checkTo(node, defnode, filtered(s))) {
            if (oldverss == null)
              oldverss = new HashMap<>();
            oldverss.put(s, defnode.getNodeVersion());// TODO is it right?
            RLogger.logError(
                "Relation on outdated node was found for " + node.getSUUId() + " and key " + s);
          }
        }
      }
      if (!isCP)
        node.getTreeDB().getTermsStorage().getTCM().setUseTerm(node.getTreeDB(), node.getSUUId(),
            node.getTreeDB().getProjectName(), useold, oldverss, use2, vers);
      if (isCP)
        getCPTermsStorage(node.getTreeDB()).getTCM().setCPUseTerm(node, cptermsold, oldverss, cpterms, vers);

    }
    if (!init)
      updateRelated(getUseToDefTermsRelations(node, null), true);// TODO filter by set
  }

  public static String filtered(String s) {
    if (s == null)
      return s;
    int i = s.indexOf(SEPARATOR), l = 0, r = s.length(), si = s.indexOf(VERSION_SEPARATOR);
    if (i > -1)
      l = i + SEPARATOR.length();
    if (s.contains(VERSION_SEPARATOR))
      r = si;
    return s.substring(l, r);
  }

  private static Map<String, List<String>> filteruseterm(String[] use2, Map<String, String> vers) {
    Map<String, List<String>> ret = null;
    if (use2 == null)
      return Collections.emptyMap();
    for (String s : use2) {
      int ind = s.indexOf(SEPARATOR);
      if (ind > -1) {
        String v = vers == null ? null : vers.remove(s);
        String p = s.substring(0, ind);

        s = s.substring(ind + SEPARATOR.length());
        if (v != null && vers != null)
          vers.put(s, v);

        if (ret == null)
          ret = new HashMap<>();
        if (!ret.containsKey(p))
          ret.put(p, new LinkedList<>());
        ret.get(p).add(s);
      }
    }
    return ret != null ? ret : Collections.emptyMap();
  }

  //
//  private List<String> getUseTerm(TreeNode node) {
//    if(node == null)
//      return null;
//    getCMM().getUseTerm("useterm", node.getUUId());
//    return set;
//  }
//  private List<String> getDefTerm(TreeNode node) {
//    if(node == null)
//      return null;
//    String id = toIdentifier(node);
//    List<String> set = cs.getAltSet("defterm", id);
//    return set;
//  }
  private static void setDefTerm(TreeNode node, String[] deftermold, String[] defterm, boolean init, boolean isCP) {
    if ((defterm == null || defterm.length == 0) && (deftermold == null || deftermold.length == 0))
      return;

    TreeDB db = node.getTreeDB();
    if (!isCP)
      node.getTreeDB().getTermsStorage().getTCM().setDefTerm(node, deftermold, defterm);
    else
      getCPTermsStorage(db).getTCM().setCPDefTerm(node, deftermold, defterm);
    if (!init)
      updateRelated(getDefToUseTermsRelations(node, true), false);// TODO mb iterate
  }

  private static void updateRelated(Map<String, Map<TreeNode, String>> k, boolean rev) {
    Set<TreeNode> up = new HashSet<>();
    for (Entry<String, Map<TreeNode, String>> en : k.entrySet())
      up.addAll(en.getValue().keySet());
    for (TreeNode nde : up) {
      nde.updateLocalAttribute(rev ? TreeNode.ATTR_TERMS_REV : TreeNode.ATTR_TERMS);
      nde.commitAttributes();
    }
  }

  private static void removeDefTerm(TreeNode node, boolean isCP) {
    TreeDB db = node.getTreeDB();

    Map<String, Map<TreeNode, String>> k = getUseToDefTermsRelations(node, null);
    if (!isCP)
      node.getTreeDB().getTermsStorage().getTCM().removeDefTerms(node);
    else
      synchronized (getCPTermsStorage(db)) {// TODO sync by cs
        getCPTermsStorage(db).getTCM().removeCPDefTerms(node);
      }
    updateRelated(k, false);
  }

  private static void removeUseTerm(TreeNode node, boolean isCP) {
    TreeDB db = node.getTreeDB();
    Map<String, Map<TreeNode, String>> k = getUseToDefTermsRelations(node, null);
    node.getTreeDB().getTermsStorage().getTCM().removeUseTerms(node.getTreeDB(), node.getSUUId());
    synchronized (getCPTermsStorage(db)) {// TODO sync by cs
      getCPTermsStorage(db).getTCM().removeCPUseTerms(node);
    }
    updateRelated(k, true);
  }

  public boolean initCaches(TreeDB dbf, IProgressMonitor mon) {
    return initCaches(false, dbf, mon);
  }

  public CacheStorage getStorage(TreeDB dbf) {
    if (db != null)
      return db.getCacheStorage();
    if (rt != null)
      return rt.getCPCacheStorage(dbf);
    return null;
  }

  public boolean initCaches(boolean ints, TreeDB db, IProgressMonitor mon) {
    if (stopped || mon.isCanceled())
      return false;
    if (db != null)
      db.removeListener(lis);
    try {
      if (db != null) {
        UUID root = db.getRootNodeUUID();
        if (root == null)
          return false;
      } else
        return false;
      // Object o =
      // getCMM().getStorage(dbfrom).getPSCM(dbfrom).getProjectSetting(TERMS_INITED);
      // if(o == null){
      CacheTransactionDescription s = null;
      CacheTransactionDescription s2 = null;
      CacheStorage stor = getTCM().getStorage(db);
      CacheStorage stor2 = getCPTermsStorage(db).getTCM().getStorage(db);
      try {
        // TODO two caches locks
        if (!isCP)
          s = stor.startCacheTransaction("init terms cache ", CacheStorage.COMMIT_ALL);
        else
          s2 = stor2.startCacheTransaction("init terms cache ", CacheStorage.COMMIT_ALL);
        DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getRootNode(), false, false, true, true, false);
        java.util.Iterator<TreeNode> it = dftw.iterator();
//          Stream<TreeNode> targetStream = StreamSupport.stream(
//              Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED),
//              false);
//          targetStream.parallel().forEach(one -> created(one));
        while (it.hasNext()) {
          if (stopped || mon.isCanceled())
            return false;
          TreeNode node = it.next();
          createdActions(node);
        }
        if (stopped || mon.isCanceled())
          return false;
        stor.getPSCM(db).setProjectSetting(TERMS_INITED + (isCP ? "-cp" : "-notCp"), "true");
      } catch (Exception e) {
        if (s != null && s.error == null) {
          s.error("already rolled back");
          stor.rollback(s);
        }
        if (s2 != null && s2.error == null) {
          s2.error("already rolled back");
          stor2.rollback(s2);
        }
        throw e;
      } finally {
        if (s2 != null && s2.error == null) {
          s2.error("already commited");
          stor2.commit(s2);
        }
        if (s != null && s.error == null) {
          s.error("already commited");
          stor.commit(s);
        }
      }
//      }
    } finally {
      if (db != null)
        db.addListener(lis);
    }
    return true;
  }

  public Map<UUID, Map<String, Map<TreeNode, String>>> getCPUseToDefTermsRelationsForNodes(TreeDB db,
      Set<TreeNode> nodes) {
    Map<UUID, Map<String, Map<TreeNode, String>>> map = null;
    Map<String, List<RelationRecord>> cpmap = null;
    List<RelationRecord> terms = getTCM().getCPUseToDefRelationsForNodes(db, nodes);
    for (RelationRecord rr : terms) {
      UUID nid = rr.nodeTo;
      if (map == null)
        map = new HashMap<>();
      if (!map.containsKey(nid))
        map.put(nid, new HashMap<>());
      TreeNode def = null;
      if (!nid.equals(rr.nodeFrom)) {
        TreeDB rf = null;
        if (rr.isCP())
          rf = db.getTracker().getTreeDB(db, null, rr.projectFrom, false, db.getMonitor());// TODO IDE monitor
        if (rf != null)
          def = rf.getNode(rr.nodeFrom);
      }
      if (def != null) {
        String key = rr.projectFrom + SEPARATOR + rr.key;
        if (!map.get(nid).containsKey(key))
          map.get(nid).put(key, new HashMap<>());
        map.get(nid).get(key).put(def, rr.nodeToVersion);
      }
    }
    return map != null ? map : Collections.emptyMap();
  }

  public Map<String, Map<TreeNode, String>> getCPUseToDefTermsRelations(TreeNode node) {
    TreeDB db = node.getTreeDB();
    Map<String, Map<TreeNode, String>> map = null;
    Map<String, List<RelationRecord>> cpmap = null;
    List<RelationRecord> terms = getTCM().getCPUseToDefRelations(node);
    for (RelationRecord rr : terms) {
      TreeNode def = node;
      UUID nid = node.getUUId();
      if (!nid.equals(rr.nodeFrom)) {
        def = null;
        TreeDB rf = null;
        if (rr.isCP())
          rf = db.getTracker().getTreeDB(db, null, rr.projectFrom, false, db.getMonitor());// TODO IDE monitor
        if (rf != null)
          def = rf.getNode(rr.nodeFrom);
      }
      if (def != null) {
        if (map == null)
          map = new HashMap<>();
        String key = rr.projectFrom + SEPARATOR + rr.key;
        if (!map.containsKey(key))
          map.put(key, new HashMap<>());
        map.get(key).put(def, rr.nodeToVersion);
      }
    }
    return map != null ? map : Collections.emptyMap();
  }

  public List<String> getCPUseToDefTermsNames(TreeNode node) {
    List<String> map = null;
    Map<String, List<RelationRecord>> cpmap = null;
    List<RelationRecord> terms = getTCM().getCPUseToDefRelations(node);
    for (RelationRecord rr : terms) {
      TreeNode def = node;
      UUID nid = node.getUUId();
      if (def != null) {
        if (map == null)
          map = new LinkedList<>();
        map.add(rr.projectFrom + SEPARATOR + rr.key);
      }
    }
    return map != null ? map : Collections.emptyList();
  }

  public static List<String> getUseToDefTermsNames(TreeNode node) {
    TreeDB db = node.getTreeDB();
    String[] defs1 = node.attrs().getStringArrayAttributeValue(USE_TERM_ALT_ATTR_NAME);
    if (defs1 == null || defs1.length == 0) {
      defs1 = node.attrs().getStringArrayAttributeValue(USE_TERM_ATTR_NAME);
      if (defs1 == null || defs1.length == 0)
        return Collections.emptyList();
    }
    List<String> map = null;
    List<RelationRecord> terms = node.getTreeDB().getTermsStorage().getTCM().getUseToDefRelations(node);
    for (RelationRecord rr : terms) {
      {
        UUID nid = node.getUUId();
        if (map == null)
          map = new ArrayList<>();
        map.add(rr.key);
      }
    }
    CacheStorage cs = db.getTracker().getCPCacheStorage(db);
    if (cs == null)
      return map != null ? map : Collections.emptyList();
    cs.readLock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
    try {
      List<String> mr = getCPTermsStorage(db).getCPUseToDefTermsNames(node);
      if (mr != null)
        for (String s : mr) {
          if (map == null)
            map = new ArrayList<>();
          if (!map.contains(s))
            map.add(s);
        }
    } finally {
      cs.readUnlock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
    }
    return map != null ? map : Collections.emptyList();
  }

  public static Map<String, Map<TreeNode, String>> getUseToDefTermsRelations(TreeNode node, String key) {
    String vers = null;
    TermsManipulator ltc = node.getTreeDB().getTermsStorage();
    if (key != null) {
      int ind = key.indexOf(VERSION_SEPARATOR);
      if (ind > -1) {
        vers = key.substring(ind + VERSION_SEPARATOR.length());
        key = key.substring(0, ind);
      }
    }

    TreeDB db = node.getTreeDB();
    String[] defs1 = node.attrs().getStringArrayAttributeValue(USE_TERM_ALT_ATTR_NAME);
    if (defs1 == null || defs1.length == 0) {
      defs1 = node.attrs().getStringArrayAttributeValue(USE_TERM_ATTR_NAME);
      if (defs1 == null || defs1.length == 0)
        return Collections.emptyMap();
    }
    Map<String, Map<TreeNode, String>> map = null;
    List<RelationRecord> terms = ltc.getTCM().getUseToDefRelations(node);
    for (RelationRecord rr : terms) {
      {
        if (key != null && !key.equals(rr.key))
          continue;
        TreeNode def = node;
        UUID nid = node.getUUId();
        if (!nid.equals(rr.nodeFrom)) {
          def = null;
          def = db.getNode(rr.nodeFrom);
        }
        if (def != null) {
          if (map == null)
            map = new HashMap<>();
          if (!map.containsKey(rr.key))
            map.put(rr.key, new HashMap<>());
          map.get(rr.key).put(def, rr.nodeToVersion);
        }
      }
    }
    CacheStorage cs = db.getTracker().getCPCacheStorage(db);
    if (cs == null)
      return map != null ? map : Collections.emptyMap();
    cs.readLock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
    try {
      Map<String, Map<TreeNode, String>> mr = getCPTermsStorage(db).getCPUseToDefTermsRelations(node);
      if (mr != null)
        for (String s : mr.keySet()) {
          if (map != null && !map.containsKey(s))
            map.put(s, mr.get(s));
          else {
            Map<TreeNode, String> set = mr.get(s);
            if (set != null) {
              if (map == null && set.size() > 0) {
                map = new HashMap<>();
                map.put(s, mr.get(s));
              }
              if (map != null)
                map.get(s).putAll(set);
            }
          }
        }
    } finally {
      cs.readUnlock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
    }
    return map != null ? map : Collections.emptyMap();
  }

  public static Map<UUID, Map<String, Map<TreeNode, String>>> getUseToDefTermsRelationsForNodes(TreeDB tree,
      Set<TreeNode> nodes, boolean withcp) {
    String vers = null;
    TermsManipulator ltc = tree.getTermsStorage();

    Map<UUID, Map<String, Map<TreeNode, String>>> map = null;
    List<RelationRecord> terms = ltc.getTCM().getUseToDefRelationsForNodes(tree, nodes);
    for (RelationRecord rr : terms) {
      {
        if (map == null)
          map = new HashMap<>();

        if (!map.containsKey(rr.nodeTo))
          map.put(rr.nodeTo, new HashMap<>());

        TreeNode def = tree.getNode(rr.nodeFrom);
        if (def != null) {
          if (!map.get(rr.nodeTo).containsKey(rr.key))
            map.get(rr.nodeTo).put(rr.key, new HashMap<>());
          map.get(rr.nodeTo).get(rr.key).put(def, rr.nodeToVersion);
        }
      }
    }
    if (withcp) {
      CacheStorage cs = tree.getTracker().getCPCacheStorage(tree);
      if (cs == null)
        return map != null ? map : Collections.emptyMap();
      cs.readLock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      try {
        Map<UUID, Map<String, Map<TreeNode, String>>> mr = getCPTermsStorage(tree)
            .getCPUseToDefTermsRelationsForNodes(tree, nodes);
        if (mr != null)
          for (UUID uid : mr.keySet())
            for (String s : mr.get(uid).keySet()) {
              if (map != null && !map.containsKey(uid))
                map.put(uid, mr.get(uid));
              else {
                if (map != null && !map.containsKey(uid))
                  map.get(uid).put(s, mr.get(uid).get(s));
                else {
                  Map<TreeNode, String> set = mr.get(uid).get(s);
                  if (set != null && map != null) {
                    map.get(uid).get(s).putAll(set);
                  }
                }
              }
            }
      } finally {
        cs.readUnlock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      }
    }
    return map != null ? map : Collections.emptyMap();
  }

  private Map<String, Map<TreeNode, String>> getCPDefToUseTerms(TreeNode node,
      Map<String, List<RelationRecord>> relations) {
    TreeDB db = node.getTreeDB();
    Map<String, Map<TreeNode, String>> map = null;
    List<RelationRecord> terms = relations == null ? getTCM().getCPDefToUseRelations(node)
        : getTCM().getCPDefToUseRelations(node.getTreeDB(), relations);
    for (RelationRecord rr : terms) {
      TreeNode def = node;
      UUID nid = node.getUUId();
      if (!nid.equals(rr.nodeTo)) {
        def = null;
        TreeDB pf = null;
        if (rr.isCP())
          pf = db.getTracker().getTreeDB(db, null, rr.projectTo, false, db.getMonitor());// TODO IDE monitor
        if (pf != null)
          def = pf.getNode(rr.nodeTo);
      }
      if (def != null) {
        boolean cp = rr.isCP();
        if (map == null)
          map = new HashMap<>();
        String key = rr.key;// not similar to use, defs are always local
        if (!map.containsKey(key))
          map.put(key, new HashMap<>());
        map.get(key).put(def, rr.nodeToVersion);
      }
    }
    return map != null ? map : Collections.emptyMap();
  }

  private Map<UUID, Map<String, Map<TreeNode, String>>> getAllCPDefToUseTerms(TreeDB db) {
    Map<UUID, Map<String, Map<TreeNode, String>>> map = null;
    List<RelationRecord> terms = getTCM().getAllCPDefToUseRelations(db);
    for (RelationRecord rr : terms) {
      if (map == null)
        map = new HashMap<>();
      map.put(rr.nodeFrom, new HashMap<>());

      TreeNode def = null;
      TreeDB pf = null;
      if (rr.isCP())
        pf = db.getTracker().getTreeDB(db, null, rr.projectTo, false, db.getMonitor());// TODO IDE monitor
      if (pf != null)
        def = pf.getNode(rr.nodeTo);
      if (def != null) {
        boolean cp = rr.isCP();
        String key = rr.key;// not similar to use, defs are always local
        Map<String, Map<TreeNode, String>> nodeFromMap = map.get(rr.nodeFrom);
        if (!nodeFromMap.containsKey(key))
          nodeFromMap.put(key, new HashMap<>());
        nodeFromMap.get(key).put(def, rr.nodeToVersion);
      }
    }
    return map != null ? map : Collections.emptyMap();
  }

  private List<String> getCPDefToUseNames(TreeNode node, Map<String, List<RelationRecord>> relations) {
    List<String> map = null;

    List<RelationRecord> terms = relations == null ? getTCM().getCPDefToUseRelations(node)
        : getTCM().getCPDefToUseRelations(node.getTreeDB(), relations);
    for (RelationRecord rr : terms) {
      if (map == null)
        map = new LinkedList<>();
      map.add(rr.key);// not similar to use, defs are always local
    }
    return map != null ? map : Collections.emptyList();
  }

  public static Map<TreeNode, String> getDefToUseTermsRelations(TreeNode node, boolean withcp, String key) {
    TreeDB db = node.getTreeDB();

    String[] defs1 = node.attrs().getStringArrayAttributeValue(DEF_TERM_ATTR_NAME);
    if (defs1 == null || defs1.length == 0)
      return Collections.emptyMap();
    UUID or = node.attrs().getAttribute(DEF_TERM_ATTR_NAME).getOrigin();
    if (or != node.getUUId())
      return Collections.emptyMap();
    Map<TreeNode, String> map = null;
    Map<String, List<RelationRecord>> cpmap = null;
    List<RelationRecord> terms = node.getTreeDB().getTermsStorage().getTCM().getDefToUseRelations(node, key);
    for (RelationRecord rr : terms) {
      if (rr.isCP()) {
        if (cpmap == null)
          cpmap = new HashMap<>();
        if (!cpmap.containsKey(rr.projectTo))
          cpmap.put(rr.projectTo, new LinkedList<>());
        cpmap.get(rr.projectTo).add(rr);
      } else {
        TreeNode def = node;
        UUID nid = node.getUUId();
        if (!nid.equals(rr.nodeTo)) {
          def = null;
          def = db.getNode(rr.nodeTo);
        }
        if (def != null) {
          if (map == null)
            map = new HashMap<>();
          map.put(def, rr.nodeToVersion);
        }
      }
    }
    if (withcp) {
      CacheStorage cs = db.getTracker().getCPCacheStorage(db);
      if (cs == null)
        return map != null ? map : Collections.emptyMap();
      cs.readLock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      try {
        Map<String, Map<TreeNode, String>> mr = getCPTermsStorage(db).getCPDefToUseTerms(node, null);
        String s = key;
        if (mr != null) {
          if (map == null && mr.size() > 0) {
            map = new HashMap<>();
            Map<TreeNode, String> fmap = map;
            mr.entrySet().forEach(one -> {
              if (getFilteredKey(one.getKey()).equals(key))
                fmap.putAll(one.getValue());
            });
          } else if (map != null) {
            Map<TreeNode, String> fmap = map;
            mr.entrySet().forEach(one -> {
              if (getFilteredKey(one.getKey()).equals(key))
                fmap.putAll(one.getValue());
            });
          }
        }
      } finally {
        cs.readUnlock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      }
    }
    return map != null ? map : Collections.emptyMap();
  }

  private static String getFilteredKey(String key) {
    if (key == null)
      return null;
    int projPos = key.indexOf("::");
    if (projPos > 0)
      return key.substring(projPos + 2);
    return key;
  }

  public static Map<String, Map<TreeNode, String>> getDefToUseTermsRelations(TreeNode node, boolean withcp) {
    TreeDB db = node.getTreeDB();

    String[] defs1 = node.attrs().getStringArrayAttributeValue(DEF_TERM_ATTR_NAME);
    if (defs1 == null || defs1.length == 0)
      return Collections.emptyMap();
    UUID or = node.attrs().getAttribute(DEF_TERM_ATTR_NAME).getOrigin();
    if (!or.equals(node.getUUId()))
      return Collections.emptyMap();
    Map<String, Map<TreeNode, String>> map = null;
    Map<String, List<RelationRecord>> cpmap = null;
    List<RelationRecord> terms = node.getTreeDB().getTermsStorage().getTCM().getDefToUseRelations(node);
    for (RelationRecord rr : terms) {
      if (rr.isCP()) {
        if (cpmap == null)
          cpmap = new HashMap<>();
        if (!cpmap.containsKey(rr.projectTo))
          cpmap.put(rr.projectTo, new LinkedList<>());
        cpmap.get(rr.projectTo).add(rr);
      } else {
        TreeNode def = node;
        UUID nid = node.getUUId();
        if (!nid.equals(rr.nodeTo)) {
          def = null;
          def = db.getNode(rr.nodeTo);
        }
        if (def != null) {
          if (map == null)
            map = new HashMap<>();
          if (!map.containsKey(rr.key))
            map.put(rr.key, new HashMap<>());
          map.get(rr.key).put(def, rr.nodeToVersion);
        }
      }
    }
    if (withcp) {
      CacheStorage cs = db.getTracker().getCPCacheStorage(db);
      if (cs == null)
        return map != null ? map : Collections.emptyMap();
      cs.readLock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      try {
        Map<String, Map<TreeNode, String>> mr = getCPTermsStorage(db).getCPDefToUseTerms(node, null);
        ;
        if (mr != null)
          for (String s : mr.keySet()) {
            if (map != null && !map.containsKey(s)) {
              map.put(s, mr.get(s));
            } else {
              Map<TreeNode, String> set = mr.get(s);
              if (set != null) {
                if (map == null && set.size() > 0) {
                  map = new HashMap<>();
                  map.put(s, mr.get(s));
                } else if (map != null)
                  map.get(s).putAll(set);
              }
            }
          }
      } finally {
        cs.readUnlock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      }
    }
    return map != null ? map : Collections.emptyMap();
  }

  public static Map<UUID, Map<String, Map<TreeNode, String>>> getDefToUseTermsRelationsForNodes(TreeDB tree,
      Set<TreeNode> nodes, boolean withcp) {
    Map<UUID, Map<String, Map<TreeNode, String>>> ret = new HashMap<>();
    List<RelationRecord> terms = tree.getTermsStorage().getTCM().getDefToUseRelationsForNode(tree, nodes);
    for (RelationRecord rr : terms) {
      {

        if (!ret.containsKey(rr.nodeFrom))
          ret.put(rr.nodeFrom, new HashMap<>());
        UUID nid = rr.nodeTo;
        TreeNode def = tree.getNode(rr.nodeTo);
        if (def != null) {
          if (!ret.get(rr.nodeFrom).containsKey(rr.key))
            ret.get(rr.nodeFrom).put(rr.key, new HashMap<>());
          ret.get(rr.nodeFrom).get(rr.key).put(def, rr.nodeToVersion);
        }
      }
    }
    if (withcp) {
      CacheStorage cs = tree.getTracker().getCPCacheStorage(tree);
      cs.readLock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      try {
        Map<UUID, Map<String, Map<TreeNode, String>>> mr = getCPTermsStorage(tree).getAllCPDefToUseTerms(tree);
        if (mr != null)
          for (UUID uid : mr.keySet())
            for (String s : mr.get(uid).keySet()) {
              if (!ret.containsKey(uid))
                ret.put(uid, new HashMap<>());
              if (!ret.get(uid).containsKey(s))
                ret.get(uid).put(s, mr.get(uid).get(s));
              else {
                Map<TreeNode, String> set = mr.get(uid).get(s);
                if (set != null) {
                  if (set.size() > 0) {
                    ret.get(uid).get(s).putAll(set);
                  }
                }
              }
            }
      } finally {
        cs.readUnlock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
      }
    }
    return ret != null ? ret : Collections.emptyMap();
  }

  public static List<String> getDefToUseTermsNames(TreeNode node) {
    TreeDB db = node.getTreeDB();
    String[] defs1 = node.attrs().getStringArrayAttributeValue(DEF_TERM_ATTR_NAME);
    if (defs1 == null || defs1.length == 0)
      return Collections.emptyList();
    UUID or = node.attrs().getAttribute(DEF_TERM_ATTR_NAME).getOrigin();
    if (or != node.getUUId())
      return Collections.emptyList();
    List<String> map = null;
    Map<String, List<RelationRecord>> cpmap = null;
    List<RelationRecord> terms = node.getTreeDB().getTermsStorage().getTCM().getDefToUseRelations(node);
    for (RelationRecord rr : terms)
      if (rr.isCP()) {
        if (cpmap == null)
          cpmap = new HashMap<>();
        if (!cpmap.containsKey(rr.projectTo))
          cpmap.put(rr.projectTo, new LinkedList<>());
        cpmap.get(rr.projectTo).add(rr);
      } else {
        if (map == null)
          map = new ArrayList<>();
        map.add(rr.key);
      }
    CacheStorage cs = db.getTracker().getCPCacheStorage(db);
    if (cs == null)
      return map != null ? map : Collections.emptyList();
    cs.readLock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
    try {
      List<String> mr = getCPTermsStorage(db).getCPDefToUseNames(node, cpmap);
      if (mr != null)
        for (String s : mr) {
          if (map == null)
            map = new ArrayList<>();
          if (!map.contains(s))
            map.add(s);
        }
    } finally {
      cs.readUnlock(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF);
    }
    return map != null ? map : Collections.emptyList();
  }

  public void close() {
    if (db != null)
      db.removeListener(lis);
  }

  public static Map<String, TreeNode> getDefNodes(TreeNode node, Set<String> tf) {
    if (tf == null || tf.size() == 0)
      return Collections.emptyMap();

    Map<String, TreeNode> ret = null;
    Map<String, UUID> map = node.getTreeDB().getTermsStorage().getTCM().getDefMap(node.getTreeDB(), tf);
    // CacheStorage cs = db.getTracker().getCPCacheStorage(db);
    // cs.readLock();
    try {
      Map<String, UUID> mr = getCPTermsStorage(node.getTreeDB()).getTCM().getCPDefMap(node.getTreeDB(),
          filteruseterm(tf.toArray(new String[0]), null));
      if (mr != null)
        for (String s : mr.keySet()) {
          if (map == null)
            map = new HashMap<>();
          map.putAll(mr);
        }
    } finally {
      // cs.readUnlock();
    }
    if (map != null) {
      ret = new HashMap<>();
      for (String s : map.keySet()) {
        String vers = null;
        int ind = s.indexOf(VERSION_SEPARATOR);
        if (ind > -1) {
          vers = s.substring(ind + VERSION_SEPARATOR.length());
          s = s.substring(0, ind);
        }

        ind = s.indexOf(TermsManipulator.SEPARATOR);
        TreeDB tdb = node.getTreeDB();
        if (ind > -1) {
          String dbn = s.substring(0, ind);
          // s = s.substring(ind+TermsManipulator.SEPARATOR.length());
          TreeDB a = node.getTreeDB().getTracker().getTreeDB(null, null, dbn, false,
              node.getTreeDB().getMonitor());// TODO IDE monitor
          if (a != null && a.getProjectName().equals(dbn)) {
            tdb = a;
            break;
          }
        }
        TreeNode nodea = tdb.getNode(map.get(s));
        if (nodea != null)
          ret.put(s, nodea);
      }
      return ret;
    }
    return Collections.emptyMap();
  }

  @Override
  protected OperationResult innerDropCache(TreeDB db, SettingsManipulator cman) {
    getTCM().removeAll(db);
    return OperationResult.SUCEEDED;
  }

  @Override
  protected OperationResult innerInitFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    if (db != null) {
      try {
        stopped = false;
        Global.registerStoppable(this);
        db.removeListener(lis);
        try{
          if (!initCaches(db, mon))
            return OperationResult.UNSUCEEDED;
        }finally{
          if(!db.getListeners().contains(lis))
            db.addListener(lis);
        }
      } finally {
        Global.unRegisterStoppable(this);
      }
    }
    return OperationResult.SUCEEDED;

//    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getNode("/Requirements"));
//    Iterator<TreeNode>it = dftw.iterator();
//    while(it.hasNext())
//    {
//      TreeNode node = it.next();
//      db.getTermsStorage().initCache(node);
//    }

  }

  @Override
  public boolean isCrossProjectCache() {
    return isCP;
  }

  public Pair<TreeRelationsCounter, TreeRelationsCounter> getRelatedProjects(CacheStorage cs, String lname,
      String pname) {
    return getTCM().getRelatedProject(cs, lname, pname);
  }

  @Override
  public OperationResult innerInitNotFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    if (db != null)
      return OperationResult.SUCEEDED;//TODO init needed?
    return OperationResult.UNSUCEEDED;
  }

  private static String[] C_NAMES = new String[] { TermsCacheMan.CACHE_TABLE_NAME_DEFTERM,
      TermsCacheMan.CACHE_TABLE_NAME_USEDEF, TermsCacheMan.CACHE_TABLE_NAME_RELATIONS };

  @Override
  protected String[] getSettingsNames(TreeDB db) {
    if (isCP)
      return new String[] { getCPTreeDBId(TermsCacheMan.CACHE_TABLE_NAME_CP_USEDEF, db),
          getCPTreeDBId(TermsCacheMan.CACHE_TABLE_NAME_CP_DEFTERM, db),
          getCPTreeDBId(TermsCacheMan.CACHE_TABLE_NAME_CP_RELATIONS, db) };
    else
      return C_NAMES;
  }

  private static String[] C_DEFS = new String[] { "1.2.1", "1.2.1", "1.2.1" };

  @Override
  protected String[] getVersions() {
    return C_DEFS;
  }

  @Override
  public void stop() {
    stopped = true;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected boolean isNonRevision() {
    return false;
  }
}
