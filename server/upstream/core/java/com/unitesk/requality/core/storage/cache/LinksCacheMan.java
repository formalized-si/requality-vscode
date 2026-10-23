package com.unitesk.requality.core.storage.cache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import java.util.UUID;

import com.unitesk.requality.core.attribute.reference.TreeRelationsCounter;
import com.unitesk.requality.core.attribute.reference.TreeRelationsCounter.Direction;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.CPLinkResolver;
import com.unitesk.requality.core.storage.FNames;
import com.unitesk.requality.core.storage.ISectionModel;
import com.unitesk.requality.core.storage.LateResolveRequest;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class LinksCacheMan extends CacheManager {
/**
 * Have several cp sections 
 * -late links(from->to without to project), 
 * -revert links(from->to in to)
 * and local section
 * -revert links(from->to, to side)
 */
  public static final String LATE_SECTION_NAME="LateLinks";
  public static final String REVERT_LINKS_SECTION_NAME="LinksCache";
  private static final String REVS_MODEL_VERSION="1.2.3",
                              GLOB_REVS_MODEL_VERSION="1.2.3",
                              LATES_MODEL_VERSION="1.2.3";
  private static final String SQL_REMOVE_REV = "DELETE FROM "+REVERT_LINKS_SECTION_NAME+" WHERE "+
      FNames.NODE_FROM+" = ? AND "+FNames.NODE_TO+" = ? AND "+FNames.ATTR_NAME+" = ?;";
  private static final String SQL_REMOVE_REV_ALL = "DELETE FROM "+REVERT_LINKS_SECTION_NAME+";";
  private static final String SQL_ADD_REV = "INSERT OR REPLACE INTO "+
      REVERT_LINKS_SECTION_NAME+" ("+FNames.NODE_FROM+", "+FNames.NODE_TO+", "+FNames.ATTR_NAME+", "+FNames.NODE_TO_VERSION+
      ") VALUES (?, ?, ?, ?);";
  private static final String SQL_REMOVE_REV_BY_NODE = "DELETE FROM "+REVERT_LINKS_SECTION_NAME+" WHERE "+
      FNames.NODE_FROM+" = ? OR "+FNames.NODE_TO+" = ?;";
  private static final String SQL_REMOVE_LATE_BY_NODE = "DELETE FROM "+LATE_SECTION_NAME+" WHERE "+
      FNames.LPROJ_FROM+" = ? AND "+FNames.NODE_FROM+" = ?;";
  private static final String SQL_GET_DIRECT_BY_LIST_START = "SELECT * FROM "+REVERT_LINKS_SECTION_NAME+" WHERE "
      +FNames.NODE_FROM+" = ?";
  private static final String SQL_GET_DIRECT = "SELECT * FROM "+REVERT_LINKS_SECTION_NAME+" WHERE "
      +FNames.NODE_FROM+" = ?;";
  private static final String SQL_GET_ALL_DIRECT = "SELECT * FROM "+REVERT_LINKS_SECTION_NAME+";";
  private static final String SQL_GET_REVERT = "SELECT * FROM "+REVERT_LINKS_SECTION_NAME+" WHERE "
      +FNames.NODE_TO+" = ?;";
  private static final String SQL_GET_REVERT_BY_KEY = "SELECT * FROM "+REVERT_LINKS_SECTION_NAME+" WHERE "
      +FNames.NODE_TO+" = ? AND "+FNames.ATTR_NAME+" = ?;";

  private static final String SQL_GET_ALL_REVERT = "SELECT * FROM "+REVERT_LINKS_SECTION_NAME+";";
  private static final String SQL_GET_REVERT_BY_LIST_START = "SELECT * FROM "+REVERT_LINKS_SECTION_NAME+" WHERE "
      +FNames.NODE_TO+" = ?";
  private static final String INSERT_LATE_SQL = "INSERT INTO "
      +LATE_SECTION_NAME
      +"("+FNames.LPROJ_FROM+", "+FNames.PROJ_FROM+", "+FNames.PROJ_TO+", "+FNames.NODE_FROM+", "+FNames.ATTR_NAME+") "
      +"VALUES (?, ?, ?, ?, ?);"; 
  private static final String REMOVE_LATE_SQL = "DELETE FROM "
      +LATE_SECTION_NAME
      + " WHERE "+FNames.LPROJ_FROM+" = ? AND "+FNames.PROJ_TO+" = ? AND "+FNames.NODE_FROM+" = ? AND "+FNames.ATTR_NAME+" = ?;";
  private static final String SELECT_LATES_SQL = "SELECT * FROM "+LATE_SECTION_NAME+";";
//  private static final String SQL_REMOVE_GLOB_REV_LINKS_BY_PROJECT = "DELETE FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+
//      " WHERE "+FNames.PROJ_FROM +" = ? OR "+FNames.PROJ_TO +" = ?;";
//  private static final String SQL_REMOVE_LATES_BY_PROJECT = "DELETE FROM "+LATE_SECTION_NAME+
//      " WHERE "+FNames.PROJ_FROM +" = ? OR "+FNames.PROJ_TO +" = ?;";
  
  public static final String GLOBAL_REVERT_LINKS_SECTION_NAME="GlobalRevertLinks";
  private static final String SQL_CP_REMOVE_REV = "DELETE FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "+
    FNames.LPROJ_FROM+" = ? AND "+FNames.PROJ_FROM+" = ? AND "+FNames.NODE_FROM+" = ? AND "+
    FNames.LPROJ_TO+" = ? AND "+FNames.PROJ_TO+" = ? AND "+FNames.NODE_TO+" = ? AND "+FNames.ATTR_NAME+" = ?;";
  private static final String SQL_CP_ADD_REV = "INSERT OR REPLACE INTO "+
      GLOBAL_REVERT_LINKS_SECTION_NAME+" ("+FNames.LPROJ_FROM+" ,"+FNames.PROJ_FROM+", "+FNames.NODE_FROM+
       ", "+FNames.LPROJ_TO+", "+FNames.PROJ_TO+", "+FNames.NODE_TO+", "+FNames.ATTR_NAME+", "+FNames.NODE_TO_VERSION+
      ") VALUES (?,?,?, ?, ?, ?, ?, ?);";
  private static final String SQL_CP_SEL_REL_PROJ_BY_PROJ = "SELECT DISTINCT "+FNames.LPROJ_FROM+", "+FNames.LPROJ_TO+" FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "+
      FNames.LPROJ_TO+" = ? OR "+FNames.PROJ_TO+" = ? OR "+FNames.LPROJ_FROM+" = ? OR "+FNames.PROJ_FROM+" = ?;";
  private static final String SQL_CP_SEL_REL_BY_PROJ = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "+
      FNames.LPROJ_TO+" = ? OR "+FNames.PROJ_TO+" = ? OR "+FNames.LPROJ_FROM+" = ? OR "+FNames.PROJ_FROM+" = ?;";
  private static final String SQL_CP_SEL_REV_BY_PROJ_FROM = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "+
      FNames.LPROJ_FROM+" = ? OR "+FNames.PROJ_FROM+" = ?;";

  private static final String SQL_CP_REMOVE_REV_BY_PROJ = "DELETE FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "+
      FNames.LPROJ_FROM+" = ? OR "+FNames.LPROJ_TO+" = ?;";
  
  private static final String SQL_REMOVE_CP_REV_BY_NODE = "DELETE FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "+
      FNames.LPROJ_FROM+" = ? AND "+FNames.NODE_FROM+" = ? OR "+FNames.LPROJ_TO+" = ? AND "+FNames.NODE_TO+" = ?;";
  private static final String SQL_CP_GET_DIRECT_BY_LIST_START = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
      +FNames.LPROJ_FROM+" = ? AND "
      +FNames.NODE_FROM+" = ?";
  private static final String SQL_CP_GET_DIRECT = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
      + FNames.LPROJ_FROM+" = ? AND "
      +FNames.NODE_FROM+" = ?;";
  private static final String SQL_CP_GET_DIRECT_SUBSET_PREFIX = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
        + FNames.LPROJ_FROM+" = ? AND "
        +FNames.NODE_FROM+" IN (";
  private static final String SQL_CP_GET_ALL_DIRECT = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
      + FNames.LPROJ_FROM+" = ?;";
  private static final String SQL_CP_GET_REVERT_BY_LIST_START = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
      + FNames.LPROJ_TO+" = ? AND "
      +FNames.NODE_TO+" = ?";
  private static final String SQL_CP_GET_REVERT = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
      + FNames.LPROJ_TO+" = ? AND "
      +FNames.NODE_TO+" = ?;";
  private static final String SQL_CP_GET_REVERT_BY_KEY = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
      + FNames.LPROJ_TO+" = ? AND "
      +FNames.NODE_TO+" = ? AND "
      +FNames.ATTR_NAME+"=?;";
  private static final String SQL_CP_GET_ALL_REVERT = "SELECT * FROM "+GLOBAL_REVERT_LINKS_SECTION_NAME+" WHERE "
      + FNames.LPROJ_TO+" = ?;";
  
  public Set<String> treeCacheInited = null;
  public Map<UUID, List<RelationRecord>> cachedRelations = null;
  
  private static ISectionModel revGlobalModel = new ISectionModel(){
    @Override
    public String getSectionName() {
      return GLOBAL_REVERT_LINKS_SECTION_NAME;
    }

    private Map<String, CType> definition = new HashMap<>();
    private Set<String> idFields = new HashSet<>();
    {
      definition.put(FNames.LPROJ_FROM, CType.STRING);
      definition.put(FNames.LPROJ_TO, CType.STRING);
      definition.put(FNames.PROJ_FROM, CType.STRING);
      definition.put(FNames.PROJ_TO, CType.STRING);
      definition.put(FNames.NODE_FROM, CType.STRING);
      definition.put(FNames.ATTR_NAME, CType.STRING);
      definition.put(FNames.NODE_TO, CType.STRING);
      definition.put(FNames.NODE_TO_VERSION, CType.STRING);
      idFields.add(FNames.LPROJ_FROM);
      idFields.add(FNames.LPROJ_TO);
      idFields.add(FNames.NODE_FROM);
      idFields.add(FNames.ATTR_NAME);
      idFields.add(FNames.NODE_TO);
    }

    @Override
    public String getVersion() {
      return GLOB_REVS_MODEL_VERSION;
    }

    @Override
    public Map<String, CType> getSectionDefinition() {
      return definition;
    }
  
    @Override
    public Set<String> getIdFields() {
      return idFields;
    }
  
    @Override
    public Set<String> getAutoIncrementedFields() {
      return Collections.emptySet();
    }


    @Override
    public List<String> getTriggers() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public short getPriority() {
      return NORMAL;
    }
  };
  private static ISectionModel latesModel = new ISectionModel(){
    @Override
    public String getSectionName() {
      return LATE_SECTION_NAME;
    }

    private Map<String, CType> definition = new HashMap<>();
    private Set<String> idFields = new HashSet<>();
    {
      definition.put(FNames.LPROJ_FROM, CType.STRING);
      definition.put(FNames.PROJ_FROM, CType.STRING);
      definition.put(FNames.PROJ_TO, CType.STRING);
      definition.put(FNames.NODE_FROM, CType.STRING);
      definition.put(FNames.ATTR_NAME, CType.STRING);
      idFields.add(FNames.LPROJ_FROM);
      idFields.add(FNames.PROJ_TO);
      idFields.add(FNames.NODE_FROM);
      idFields.add(FNames.ATTR_NAME);
    }
    @Override
    public String getVersion() {
      return LATES_MODEL_VERSION;
    }
    @Override
    public Map<String, CType> getSectionDefinition() {
      return definition;
    }
  
    @Override
    public Set<String> getIdFields() {
      return idFields;
    }
  
    @Override
    public Set<String> getAutoIncrementedFields() {
      return Collections.emptySet();
    }

    @Override
    public List<String> getTriggers() {
      // TODO Auto-generated method stub
      return null;
    }
    @Override
    public short getPriority() {
      return NORMAL;
    }

  };
  private static ISectionModel revModel = new ISectionModel(){
    @Override
    public String getSectionName() {
      return REVERT_LINKS_SECTION_NAME;
    }

    private Map<String, CType> definition = new HashMap<>();
    private Set<String> idFields = new HashSet<>();
    {
      definition.put(FNames.NODE_FROM, CType.STRING);
      definition.put(FNames.ATTR_NAME, CType.STRING);
      definition.put(FNames.NODE_TO, CType.STRING);
      definition.put(FNames.NODE_TO_VERSION, CType.STRING);
      idFields.add(FNames.NODE_FROM);
      idFields.add(FNames.ATTR_NAME);
      idFields.add(FNames.NODE_TO);
    }

    @Override
    public String getVersion() {
      return REVS_MODEL_VERSION;
    }
    @Override
    public Map<String, CType> getSectionDefinition() {
      return definition;
    }
  
    @Override
    public Set<String> getIdFields() {
      return idFields;
    }
  
    @Override
    public Set<String> getAutoIncrementedFields() {
      return Collections.emptySet();
    }

    @Override
    public List<String> getTriggers() {
      return null;
    }
    @Override
    public short getPriority() {
      return NORMAL;
    }
  };
  private static int RELATED_ITERATION_CNT = 100;
  public boolean initRelatedCache(TreeNode subTreeRoot) {
    TreeDB db = subTreeRoot.getTreeDB();
    int cnt = 0;
    if(cachedRelations!=null && treeCacheInited.contains(db.getLocalName()))
      return false;
    if(cachedRelations == null)
      cachedRelations = new HashMap<>();
    StringBuilder sb = new StringBuilder(SQL_CP_GET_DIRECT_SUBSET_PREFIX);
    int cutLen = SQL_CP_GET_DIRECT_SUBSET_PREFIX.length();
    if(treeCacheInited == null)
      treeCacheInited = new HashSet<>();
    treeCacheInited.add(db.getLocalName());
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(subTreeRoot, false, false, true);
    Iterator<TreeNode> it = dftw.iterator();
    while(it.hasNext()) {
      TreeNode node = it.next();
      cnt++;
      if(sb.length() > cutLen)
        sb.append(", ");
      sb.append("'");
      UUID uid = node.getUUId();
      sb.append(uid);
      cachedRelations.put(uid, Collections.emptyList());
      sb.append("'");
      if(cnt>RELATED_ITERATION_CNT) {
        cnt = 0;
        sb.append(");");
        relatedCacheInitForSet(db, sb, cachedRelations);
        sb.setLength(cutLen);
      }
    }
    if(cnt>0) {
      sb.append(");");
      relatedCacheInitForSet(db, sb, cachedRelations);
    }
    return true;
  }
  private void relatedCacheInitForSet(TreeDB db, StringBuilder sb, Map<UUID, List<RelationRecord>> cachedRelations2) {
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(REVERT_LINKS_SECTION_NAME, sb.toString(), db.getLocalName());
    if(p == null)
      return;
    ResultSet rs = p.getSecond(); 
    try{
      while(rs.next()){
        UUID nfrom = UUID.fromString(rs.getString(FNames.NODE_FROM));
        List<RelationRecord> set = cachedRelations2.get(nfrom);
        if(set.isEmpty())
        {
          set = new LinkedList<>();
          cachedRelations2.put(nfrom, set);
        }
        set.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM), nfrom
            , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  public void freeRelatedCache(TreeDB tree) {
    String lname = tree.getLocalName(); 
    treeCacheInited.remove(lname);
    if(treeCacheInited.isEmpty()) {
      cachedRelations = null;//TODO more proper clear?
      treeCacheInited = null;
    }
  }
  public LinksCacheMan(ICacheStorageProvider cs, boolean isCp) {
    super(cs,isCp);
  }
  public void saveLates(TreeDB db, Map<String, List<LateResolveRequest>> lates) {
    saveLates(getStorage(db), lates);
  }
  public void saveLates(CacheStorage cf, Map<String, List<LateResolveRequest>> plates) {
    CacheStorage cs = cf;
    CacheTransactionDescription name = cs.startCacheTransaction("saveLates", 2);
    try{
      //getStorage().clearSection(LATE_SECTION_NAME);
      Map<String, List<LateResolveRequest>> old = null;
      Map<String, List<LateResolveRequest>> lates = null;
      synchronized (LinksCacheMan.class) {
        old = new HashMap<>(loadLates(cf));
        lates = new HashMap<>(plates);
      }
      Set<String> keys = new HashSet<>(old.keySet());
      keys.addAll(lates.keySet());
      for(String s:keys){
          List<LateResolveRequest> set = lates.get(s);
          List<LateResolveRequest> oldset =old.get(s);
          if(oldset!=null)
            oldset =  new LinkedList<>(oldset);
          old.put(s, oldset);
          if(set!=null && set != oldset) {//TODO rewrite
            set = new LinkedList<>(lates.get(s));
            for(LateResolveRequest lrr:set){
              LateResolveRequest was = null;
              if(oldset!=null){
                for(LateResolveRequest olrr:oldset)
                  if(lrr.equals(olrr)){
                    was = olrr;
                    break;
                  }
              }
              if((oldset == null || !oldset.remove(lrr))&& was == null)
                cs.writeLocked(LATE_SECTION_NAME,INSERT_LATE_SQL,lrr.localProjName, lrr.projName, s, lrr.uid.toString(), lrr.key);
            }
          }
      }
      for(String s:keys){
        List<LateResolveRequest> oldset = old.get(s);
        if(oldset!=null && oldset.size()>0){
          RLogger.logDebug("Remove lates lead to "+s);
          for(LateResolveRequest lrr:oldset)
            cs.writeLocked(LATE_SECTION_NAME,REMOVE_LATE_SQL, lrr.localProjName, s, lrr.uid.toString(), lrr.key);
        }
      }

    }catch(Exception r){
      RLogger.logError(r);
    }
    finally {
      if(name != null && name.error == null)
        cs.commit(name);
    }
  }
  public void saveRevLates(String lname, String pname, TreeDB db, Map<String, List<LateResolveRequest>> lates) {
    if(lname == null)
      lname = db.getLocalName();
    if(pname == null)
      pname = db.getProjectName();
    saveRevLates(lname, pname, getStorage(db), lates);
  }
  public void saveRevLates(String lname, String pname, CacheStorage cf, Map<String, List<LateResolveRequest>> plates) {
    CacheStorage cs = cf;
    CacheTransactionDescription name = cs.startCacheTransaction("saveLates", 2);
    try{
      //getStorage().clearSection(LATE_SECTION_NAME);
      Map<String, List<LateResolveRequest>> old = null;
      Map<String, List<LateResolveRequest>> lates = null;
      synchronized (LinksCacheMan.class) {
        old = new HashMap<>(loadRevLates(cf));
        lates = new HashMap<>(plates);
      }
      Set<String> keys = new HashSet<>(old.keySet());
      keys.addAll(lates.keySet());
      for(String s:keys){
          List<LateResolveRequest> setNew = lates.get(s);
          List<LateResolveRequest> oldset =old.get(s);
          if(oldset!=null)
            oldset =  new LinkedList<>(oldset);
          old.put(s, oldset);
          if(setNew!=null && setNew != oldset) {//TODO rewrite
            setNew = new LinkedList<>(setNew);
            for(LateResolveRequest lrr:setNew){
              LateResolveRequest was = null;
              if(oldset!=null){
                for(LateResolveRequest olrr:oldset)
                  if(lrr.equals(olrr)){
                    was = olrr;
                    break;
                  }
              }
              if((oldset == null || !oldset.remove(lrr))&& was == null)
                cs.writeLocked(LATE_SECTION_NAME,INSERT_LATE_SQL,lname, pname, s, lrr.uid.toString(), lrr.key);
            }
          }else if (setNew == null)
            old.remove(s);
      }
      for(String s:keys){
        List<LateResolveRequest> oldset = old.get(s);
        if(oldset!=null && oldset.size()>0){
          RLogger.logDebug("Remove lates lead from "+lname);
          for(LateResolveRequest lrr:oldset)
            cs.writeLocked(LATE_SECTION_NAME, REMOVE_LATE_SQL,lname, lrr.projName, lrr.uid.toString(), lrr.key);
        }
      }

    }catch(Exception r){
      RLogger.logError(r);
    }
    finally {
      if(name != null && name.error == null)
        cs.commit(name);
    }
  }
//  public Map<String, List<LateResolveRequest>> loadLates(TreeDB db) {
//    return loadLates(getStorage(db));
//  }
  public Map<String, List<LateResolveRequest>> loadLates(CacheStorage cf) {
    Map<String, List<LateResolveRequest>> lates = null;
    Pair<PreparedStatement, ResultSet> p = cf.readLocked(LATE_SECTION_NAME, SELECT_LATES_SQL, CacheStorage.empty);
    if(p == null)
      return Collections.emptyMap();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(lates == null)
          lates = new HashMap<>();
        String lpf = rs.getString(FNames.LPROJ_FROM);
        String pf = rs.getString(FNames.PROJ_FROM);
        String pt = rs.getString(FNames.PROJ_TO);
        String nf = rs.getString(FNames.NODE_FROM);
        String at = rs.getString(FNames.ATTR_NAME);
        if(!lates.containsKey(pt))
          lates.put(pt, new LinkedList<>());
        if(pf!=null)
          lates.get(pt).add(new LateResolveRequest(UUID.fromString(nf), lpf, pf, at));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lates!=null?lates:new HashMap<>();
  }
  public Map<String, List<LateResolveRequest>> loadRevLates(CacheStorage cf) {
    Map<String, List<LateResolveRequest>> lates = null;
    Pair<PreparedStatement, ResultSet> p = cf.readLocked(LATE_SECTION_NAME, SELECT_LATES_SQL, CacheStorage.empty);
    if(p == null)
      return Collections.emptyMap();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(lates == null)
          lates = new HashMap<>();
        String pf = rs.getString(FNames.PROJ_FROM);
        String pt = rs.getString(FNames.PROJ_TO);
        String nf = rs.getString(FNames.NODE_FROM);
        String at = rs.getString(FNames.ATTR_NAME);
        if(!lates.containsKey(pf))
          lates.put(pf, new LinkedList<>());
        if(pf!=null)
          lates.get(pf).add(new LateResolveRequest(UUID.fromString(nf), null, pt, at));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return lates!=null?lates:new HashMap<>();
  }
  public static ISectionModel getGlobalLatesCacheModel() {
    return latesModel;
  }

  public static ISectionModel getGlobalRevCacheModel() {
    return revGlobalModel;
  }
  public static ISectionModel getRevCacheModel() {
    return revModel;
  }
  public void updateLinks(TreeDB db, 
      List<RelationRecord> tod, List<RelationRecord> toa) {
    StringBuilder sb = new StringBuilder();
    sb.append("Resolve links, delete - {");
    Map<String, Map<UUID, RelationRecord>> todel = new HashMap<>();
    for(RelationRecord s:tod){
      if(!todel.containsKey(s.key))
        todel.put(s.key, new HashMap<>());
      todel.get(s.key).put(s.nodeFrom,s);
      sb.append(s);
      sb.append(" ");
    }
    sb.append("}, add - {");
    for(RelationRecord s:toa){
      Map<UUID, RelationRecord> set = todel.get(s.key);
      if(set!=null){
        RelationRecord rr = set.remove(s.nodeFrom);
        if(rr!=null)
          tod.remove(rr);
      }
      sb.append(s);
      sb.append(" ");
    }
    sb.append("}");
    CacheStorage cs = getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction(sb.toString(), CacheStorage.COMMIT_ALL);
    try{
      for(RelationRecord rr:tod)
        cs.writeLocked(REVERT_LINKS_SECTION_NAME, SQL_REMOVE_REV, rr.nodeFrom, rr.nodeTo, rr.key);
      for(RelationRecord rr:toa){
        cs.writeLocked(REVERT_LINKS_SECTION_NAME, SQL_ADD_REV, rr.nodeFrom, rr.nodeTo, rr.key, rr.nodeToVersion);
      }
    }
    catch(Exception e){
      if(tr != null && tr.error == null) {
        tr.error("already rolled back");
        cs.rollback(tr);
      }
      throw e;
    }finally {
      if(tr != null && tr.error == null)
        cs.commit(tr);
    }
  }
  public void updateCPLinks(TreeDB db, 
      List<RelationRecord> tod, List<RelationRecord> toa, boolean addLatesBack) {
    StringBuilder sb = new StringBuilder();
    sb.append("Resolve cp links, delete - {");
    Map<String, Map<UUID, RelationRecord>> todel = new HashMap<>();
    for(RelationRecord s:tod){
      if(!todel.containsKey(s.key))
        todel.put(s.key, new HashMap<>());
      todel.get(s.key).put(s.nodeFrom,s);
      sb.append(s);
      sb.append(" ");
    }
    sb.append("}, add - {");
    for(RelationRecord s:toa){
      Map<UUID, RelationRecord> set = todel.get(s.key);
      if(set!=null){
        RelationRecord rr = set.remove(s.nodeFrom);
        if(rr!=null)
          tod.remove(rr);
      }
      sb.append(s);
      sb.append(" ");
    }
    sb.append("}");
    CacheStorage cs = getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction(sb.toString(), CacheStorage.COMMIT_ALL);
    try{
      for(RelationRecord rr:tod) {
        cs.writeLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_REMOVE_REV,rr.localProjectFrom,rr.projectFrom, rr.nodeFrom,rr.localProjectTo, rr.projectTo, rr.nodeTo, rr.key);
        if(cachedRelations!=null)
          cachedRelations.getOrDefault(rr.nodeFrom, Collections.emptyList()).remove(rr);
      }
      for(RelationRecord rr:toa) {
        cs.writeLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_ADD_REV,rr.localProjectFrom,rr.projectFrom, rr.nodeFrom,rr.localProjectTo,rr.projectTo, rr.nodeTo, rr.key, rr.nodeToVersion);
        if(cachedRelations!=null) {
          List<RelationRecord> rrs = cachedRelations.get(rr.nodeFrom);
          if(rrs==null) {
            rrs = new LinkedList<>();
            cachedRelations.put(rr.nodeFrom, rrs);
          }
          rrs.add(rr);
        }
      }
      if(addLatesBack)
        for(RelationRecord rr:tod)
          addLateRecord(cs, rr.localProjectFrom, rr.projectFrom, rr.projectTo, rr.nodeFrom.toString(), rr.key);
    }
    catch(Exception e){
      if(tr != null && tr.error == null) {
        tr.error("Already rolled back");
        cs.rollback(tr);
      }
      throw e;
    }finally {
      if(tr != null && tr.error == null)
        cs.commit(tr);
    }
  }
  
  public List<RelationRecord> getCPRevLinks(TreeNode node, Set<String> ofint) {
    List<RelationRecord> rr = null;
    StringBuilder sb = new StringBuilder(SQL_CP_GET_REVERT_BY_LIST_START);
    sb.append(" AND ");
    sb.append(FNames.ATTR_NAME);
    sb.append(" IN (");
    String[]toa = ofint.toArray(new String[0]);
    for(int i = 0;i<toa.length;i++)
    {
      sb.append("?");
      if(i<(toa.length-1))
        sb.append(", ");
    }
    sb.append(")");
    Object [] args = new Object[toa.length+2];
    args[0] = node.getTreeDB().getLocalName();
    args[1] = node.getSUUId();
    for(int i = 0;i<toa.length;i++)
      args[i+2] = toa[i];
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, sb.toString(),args);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
            , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getCPRevLinks(TreeDB db, UUID uid, String key) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_GET_REVERT_BY_KEY,db.getLocalName(), uid, key);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
                , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getCPRevLinks(TreeNode node) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_GET_REVERT,node.getTreeDB().getLocalName(), node.getSUUId());;
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
            , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getAllCPRevLinks(TreeDB db) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_GET_ALL_REVERT,db.getLocalName());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
            , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getRevLinks(TreeNode node, Set<String> ofint) {
    List<RelationRecord> rr = null;

    StringBuilder sb = new StringBuilder(SQL_GET_REVERT_BY_LIST_START);
    sb.append(" AND ");
    sb.append(FNames.ATTR_NAME);
    sb.append(" IN (");
    String[]toa = ofint.toArray(new String[0]);
    for(int i = 0;i<toa.length;i++)
    {
      sb.append("?");
      if(i<(toa.length-1))
        sb.append(", ");
    }
    sb.append(")");
    Object [] args = new Object[toa.length+1];
    args[0] = node.getSUUId();
    for(int i = 0;i<toa.length;i++)
      args[i+1] = toa[i];
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(REVERT_LINKS_SECTION_NAME, sb.toString(),
        args); 
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
            , rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getAllRevLinks(TreeDB dba) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(dba).readLocked(REVERT_LINKS_SECTION_NAME, SQL_GET_ALL_REVERT);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      String proj = dba.getProjectName();
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(proj, UUID.fromString(rs.getString(FNames.NODE_FROM))
            , proj,UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getRevLinks(TreeDB db, UUID uid) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(REVERT_LINKS_SECTION_NAME, SQL_GET_REVERT, uid.toString());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      String proj = db.getProjectName();
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(proj, UUID.fromString(rs.getString(FNames.NODE_FROM))
            , proj,UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getRevLinks(TreeDB db, UUID uid, String key) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(REVERT_LINKS_SECTION_NAME, SQL_GET_REVERT_BY_KEY, uid.toString(), key);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      String proj = db.getProjectName();
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(proj, UUID.fromString(rs.getString(FNames.NODE_FROM))
            , proj,UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr == null ? Collections.emptyList():rr;
  }
  public List<RelationRecord> getRevLinks(TreeNode node) {
    return getRevLinks(node.getTreeDB(), node.getUUId());
  }
  public List<RelationRecord> getDirectLinks(TreeNode node) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(REVERT_LINKS_SECTION_NAME, SQL_GET_DIRECT, node.getSUUId());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      String proj = node.getTreeDB().getProjectName();
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(proj, UUID.fromString(rs.getString(FNames.NODE_FROM))
            , proj,UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr!=null?rr:Collections.emptyList();
  }
  public List<RelationRecord> getDirectLinks(TreeNode node, Set<String> ofint) {
    List<RelationRecord> rr = null;
    StringBuilder sb = new StringBuilder(SQL_GET_DIRECT_BY_LIST_START);
    sb.append(" AND ");
    sb.append(FNames.ATTR_NAME);
    sb.append(" IN (");
    String[]toa = ofint.toArray(new String[0]);
    for(int i = 0;i<toa.length;i++)
    {
      sb.append("?");
      if(i<(toa.length-1))
        sb.append(", ");
    }
    sb.append(")");

    Object [] args = new Object[toa.length+1];
    args[0] = node.getSUUId();
    for(int i = 0;i<toa.length;i++)
      args[i+1] = toa[i];
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(REVERT_LINKS_SECTION_NAME, sb.toString(), args);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      String proj = node.getTreeDB().getProjectName();
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(proj, UUID.fromString(rs.getString(FNames.NODE_FROM))
            , proj,UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr!=null?rr:Collections.emptyList();
  }
  public List<RelationRecord> getAllCPDirectLinks(TreeDB db) {
    List<RelationRecord> rr = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(db).readLocked(REVERT_LINKS_SECTION_NAME, SQL_CP_GET_ALL_DIRECT, db.getLocalName());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM), rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
            , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr!=null?rr:Collections.emptyList();
  }
  public List<RelationRecord> getCPDirectLinks(TreeNode node) {
    List<RelationRecord> rr = null;
    if(cachedRelations!=null) {
      rr = cachedRelations.get(node.getUUId());
      if(rr!=null)
        return rr;
    }
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(REVERT_LINKS_SECTION_NAME, SQL_CP_GET_DIRECT, node.getTreeDB().getLocalName(), node.getSUUId());
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond(); 
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
            , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr!=null?rr:Collections.emptyList();
  }
  public List<RelationRecord> getCPDirectLinks(TreeNode node, Set<String> ofint) {
    List<RelationRecord> rr = null;
    if(cachedRelations!=null) {
      List<RelationRecord> stored = cachedRelations.get(node.getUUId());
      if(stored!=null) {
        for(RelationRecord s : stored) {
          if(ofint.contains(s.key)) {
            if(rr == null) 
              rr = new LinkedList<>();
            rr.add(s);
          }
        }
      }
      return rr == null ? Collections.emptyList() : rr;
    }
    StringBuilder sb = new StringBuilder(SQL_CP_GET_DIRECT_BY_LIST_START);
    sb.append(" AND ");
    sb.append(FNames.ATTR_NAME);
    sb.append(" IN (");
    String[]toa = ofint.toArray(new String[0]);
    for(int i = 0;i<toa.length;i++)
    {
      sb.append("?");
      if(i<(toa.length-1))
        sb.append(", ");
    }
    sb.append(")");

    Object [] args = new Object[toa.length+2];
    args[0] = node.getTreeDB().getLocalName();
    args[1] = node.getSUUId();
    for(int i = 0;i<toa.length;i++)
      args[i+2] = toa[i];
    Pair<PreparedStatement, ResultSet> p = getStorage(node.getTreeDB()).readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, sb.toString(), args);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM), UUID.fromString(rs.getString(FNames.NODE_FROM))
            , rs.getString(FNames.LPROJ_TO), rs.getString(FNames.PROJ_TO),UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr!=null?rr:Collections.emptyList();
  }
  public void removeRecords(TreeDB db, UUID node) {
    CacheStorage cs = getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction("clear cache for "+node+" (ls)", CacheStorage.COMMIT_ALL);
    try{
      String suid = node.toString();
      cs.writeLocked(REVERT_LINKS_SECTION_NAME, SQL_REMOVE_REV_BY_NODE, suid, suid);
    }catch(Exception r){
      if(tr != null && tr.error == null) {
        tr.error("already rolled back");
        cs.rollback(tr);
      }
      r.printStackTrace();
    }finally {
      if(tr != null && tr.error == null)
        cs.commit(tr);
    }
  }
  public void removeCPRecords(TreeDB db, String projLocalName, String projProjectName, UUID node) {
    //TODO two locs
    CacheStorage cs = getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction("clear cache for "+node+" (ls glob)", CacheStorage.COMMIT_SINGLE);
    try{
      String nuid = node.toString();
      if(cachedRelations!=null)
        cachedRelations.remove(node);
      cs.writeLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_REMOVE_CP_REV_BY_NODE, projLocalName, nuid, projLocalName, nuid);
      cs.writeLocked(LATE_SECTION_NAME, SQL_REMOVE_LATE_BY_NODE, projLocalName, nuid);
    }catch(Exception r){
      if(tr != null && tr.error == null) {
        tr.error("already rolled back");
        cs.rollback(tr);
      }
      r.printStackTrace();
    }finally {
      if(tr != null && tr.error == null)
        cs.commit(tr);
    }
  }
  public static List<RelationRecord> getCPLinksByProject(CacheStorage cs, String lName, String pName) {
    Pair<PreparedStatement, ResultSet> p = cs.readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_SEL_REL_BY_PROJ, lName, pName, lName, pName);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    List<RelationRecord> ret = null;
    try{
      while(rs.next()){
        if(ret == null)
          ret = new LinkedList<>();
        ret.add(new RelationRecord(rs.getString(FNames.LPROJ_FROM),rs.getString(FNames.PROJ_FROM),
          UUID.fromString(rs.getString(FNames.NODE_FROM)),
          rs.getString(FNames.LPROJ_TO),rs.getString(FNames.PROJ_TO),
          UUID.fromString(rs.getString(FNames.NODE_TO)),
          rs.getString(FNames.ATTR_NAME),
          rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    if(ret == null)
      return Collections.emptyList();
    return ret;
  }
  public void removeAllCPRevCacheByProject(TreeDB db, String lName, String pName) {
    removeAllCPRevCacheByProject(db.getTracker().getCPLinkResolver(db),getStorage(db), lName, pName);
  }
  public static void removeAllCPRevCacheByProject(CPLinkResolver lr, CacheStorage cs, String lName, String pName) {
    // TODO Auto-generated method stub

    List<RelationRecord> ret = getCPLinksByProject(cs, lName, pName);
    //removeAllCPRevCacheByProject(lr, cs, resName);//removes links from and to project
    cs.writeLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_REMOVE_REV_BY_PROJ, lName, lName);

    if(ret == null)
      return;
    
    synchronized (LinksCacheMan.class) {
      if(ret.size()>0) {
        Map<String, List<LateResolveRequest>> map = new HashMap<>(lr.loadLatesRelationsRecordsFromList(ret));
        Map<String, List<LateResolveRequest>> map2 = lr.getLCM().loadLates(cs);//TODO true?
        map2.entrySet().forEach(one->{if(!map.containsKey(one.getKey()))map.put(one.getKey(), one.getValue());});
        lr.saveLatesRelationsToCache(cs, map);
      }
    }
  }
  public void removeLocalRevCache(TreeDB db) {
    CacheStorage cs = getStorage(db);
    CacheTransactionDescription tr = cs.startCacheTransaction("clear cache for "+db.getVisibleName()+" (ls)", CacheStorage.COMMIT_SINGLE);
    try{
      cs.writeLocked(REVERT_LINKS_SECTION_NAME, SQL_REMOVE_REV_ALL);
    }catch(Exception r){
      if(tr != null && tr.error == null) {
        tr.error("already rolled back");
        cs.rollback(tr);
      }
      r.printStackTrace();
    }finally {
      if(tr != null && tr.error == null)
        cs.commit(tr);
    }
  }
  public static void removeLates(TreeDB db, String name) {
    CPLinkResolver lr = db.getTracker().getCPLinkResolver(db);
    synchronized(lr.getLatesLockObject(db, name)) {
      Map<String, List<LateResolveRequest>> lates = lr.getLatesRelationsRecords(db);
      for(String s:lates.keySet())
      {
        List<LateResolveRequest> todel = new LinkedList<>();
        List<LateResolveRequest> lrr = lates.get(s);//TODO mb change type of list
        for(LateResolveRequest lra:lrr){
          if(lra == null || lra.projName == null && lra.localProjName == null)
            RLogger.logInfo("null late resolve request "+name);
          else
            if(lra.projName!=null && lra.projName.equals(name) || 
              lra.localProjName!=null&&lra.localProjName.equals(name))
              todel.add(lra);
        }
        if(!todel.isEmpty())
          lrr.removeAll(todel);
      }
      lr.saveLatesRelationsToCache(db, lates);
    }
  }
  public Pair<TreeRelationsCounter, TreeRelationsCounter> getRelatedProject(CacheStorage cs, String lname, String pname) {
    Pair<TreeRelationsCounter, TreeRelationsCounter> ret = null;
    String target = lname;
    Pair<PreparedStatement, ResultSet> p = cs.readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_SEL_REL_BY_PROJ, lname, pname, lname, pname);
    if(p != null){
      ResultSet rs = p.getSecond();
      try{
        while(rs.next()){
          String lfrom = rs.getString(FNames.LPROJ_FROM);
          if(lfrom.equals(target))
            continue;
          TreeRelationsCounter sec = null;
          if(ret == null) {
            sec = new TreeRelationsCounter(Direction.Backward, new HashMap<>(), new HashMap<>());
            ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(TreeRelationsCounter.EMPTY, sec);
          }else {
            sec = ret.getSecond();
            if(sec.isEmpty())
            {
              sec = new TreeRelationsCounter(Direction.Backward, new HashMap<>(), new HashMap<>());
              ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(ret.getFirst(), sec);              
            }
          }
          sec.addRecord(lfrom, rs.getString(FNames.ATTR_NAME), false);
        }
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      finally{
        try {
          rs.close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        try {
          p.getFirst().close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    p = cs.readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_SEL_REV_BY_PROJ_FROM, target, target);
    if(p != null){
      ResultSet rs = p.getSecond();
      try{
        while(rs.next()){

          String lto = rs.getString(FNames.LPROJ_TO);
          if(lto.equals(target))
            continue;
          TreeRelationsCounter frst = null;
          if(ret == null) {
            frst = new TreeRelationsCounter(Direction.Forward, new HashMap<>(), new HashMap<>());
            ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(frst, TreeRelationsCounter.EMPTY);
          }else {
            frst = ret.getFirst();
            if(frst.isEmpty()) {
              frst = new TreeRelationsCounter(Direction.Forward, new HashMap<>(), new HashMap<>());
              ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(frst, ret.getSecond());
            }
          }
          frst.addRecord(lto, rs.getString(FNames.ATTR_NAME), false);
        }
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      finally{
        try {
          rs.close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        try {
          p.getFirst().close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      }
      if(ret == null)
        return TreeRelationsCounter.EMPTY_PAIR;
      return ret;
    }
  //TODO update method to this
  public Pair<TreeRelationsCounter, TreeRelationsCounter> getRelatedProjectUpdated(CacheStorage cs, String lname, String pname) {
    Pair<TreeRelationsCounter, TreeRelationsCounter> ret = null;
    String target = lname;
    Pair<PreparedStatement, ResultSet> p = cs.readLocked(GLOBAL_REVERT_LINKS_SECTION_NAME, SQL_CP_SEL_REL_PROJ_BY_PROJ, lname, pname, lname, pname);
    if(p != null){
      ResultSet rs = p.getSecond();
      try{
        while(rs.next()){
          String from = rs.getString(FNames.LPROJ_FROM);
          String to = rs.getString(FNames.LPROJ_TO);
          boolean isFrom =  from.equals(target);
          boolean isTo = to.equals(target);//forw
          if(!isFrom && !isTo)
            continue;
          TreeRelationsCounter sec = null;
          Direction dir = isTo ? Direction.Forward:Direction.Backward;
          String another = isFrom ? to : from; 
          if(ret == null) {
            sec = new TreeRelationsCounter(dir, new HashMap<>(), new HashMap<>());
            ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(TreeRelationsCounter.EMPTY, sec);
          }else {
            sec = ret.getSecond();
            if(sec.isEmpty())
            {
              sec = new TreeRelationsCounter(dir, new HashMap<>(), new HashMap<>());
              ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(ret.getFirst(), sec);
            }
          }
          sec.addRecord(another, rs.getString(FNames.ATTR_NAME), false);
        }
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      finally{
        try {
          rs.close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        try {
          p.getFirst().close();
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    if(ret == null)
      return TreeRelationsCounter.EMPTY_PAIR;
    return ret;
  }
  public Pair<TreeRelationsCounter, TreeRelationsCounter> getLateRelatedProject(CacheStorage cs, String lname, String pname, Map<String, List<LateResolveRequest>> map) {
    Pair<TreeRelationsCounter, TreeRelationsCounter> ret = null;
    String target = pname;
    if(map!=null)
      for(Entry<String, List<LateResolveRequest>> en:map.entrySet()) {
        String dbName = en.getKey();
        if(dbName.equals(pname)) {
          TreeRelationsCounter trc = null;
          Map<String,Map<String,Integer>> mapR = null;
          for(LateResolveRequest lt: en.getValue()) {
            if(ret == null){
              mapR = new HashMap<>();
              trc = new TreeRelationsCounter(Direction.Backward, mapR, Collections.emptyMap());
              ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(
                TreeRelationsCounter.EMPTY, trc);
            }else{
              trc = ret.getSecond();
              mapR = trc.linksInfo;
              if(mapR == null || mapR.isEmpty())
              {
                mapR = new HashMap<>();
                trc = new TreeRelationsCounter(Direction.Backward, mapR, Collections.emptyMap());
                ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(
                  ret.getFirst(), trc); 
              }
            }
            trc.addRecord(lt.localProjName, lt.key, false);
          }
        }else
          for(LateResolveRequest lt: en.getValue()) {
            if(lt.projName!=null && lt.projName.equals(pname) || lt.localProjName.equals(lname)){
              TreeRelationsCounter trc = null;
              Map<String,Map<String,Integer>> mapR = null;
              if(ret == null){
                mapR = new HashMap<>();
                trc = new TreeRelationsCounter(Direction.Forward, mapR, Collections.emptyMap());
                ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(
                  trc, TreeRelationsCounter.EMPTY);
              }else{
                trc = ret.getFirst();
                mapR = trc.linksInfo;
                if(mapR == null || mapR.isEmpty())
                {
                  mapR = new HashMap<>();
                  trc = new TreeRelationsCounter(Direction.Forward, mapR, Collections.emptyMap());
                  ret = new Pair<TreeRelationsCounter, TreeRelationsCounter>(
                    trc, ret.getSecond()); 
                }
              }
              trc.addRecord(dbName, lt.key, false);
            }
          }
      }
    if(ret == null)
      return TreeRelationsCounter.EMPTY_PAIR;
    return ret;
  }
  public List<RelationRecord> getDirectLinksForNodes(TreeDB dba, Collection<TreeNode> nodes) {
    List<RelationRecord> rr = null;
    
    Pair<PreparedStatement, ResultSet> p = getStorage(dba).readLocked(REVERT_LINKS_SECTION_NAME, SQL_GET_ALL_DIRECT);
    if(p == null)
      return Collections.emptyList();
    ResultSet rs = p.getSecond();
    try{
      String proj = dba.getProjectName();
      while(rs.next()){
        if(rr == null)
          rr = new LinkedList<>();
        rr.add(new RelationRecord(proj, UUID.fromString(rs.getString(FNames.NODE_FROM))
            , proj,UUID.fromString(rs.getString(FNames.NODE_TO)),rs.getString(FNames.ATTR_NAME), rs.getString(FNames.NODE_TO_VERSION)));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        if(rs!=null)
          rs.close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return rr!=null?rr:Collections.emptyList();
  }
  public void addLateRecord(CacheStorage cs, String lname, String pname, String s, String uuid, String key) {
    if(pname == null)
      return;
    cs.writeLocked(LATE_SECTION_NAME, INSERT_LATE_SQL, lname, pname, s, uuid, key);
  }
  
}
