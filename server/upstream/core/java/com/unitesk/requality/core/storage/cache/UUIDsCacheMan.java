package com.unitesk.requality.core.storage.cache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.storage.FNames;
import com.unitesk.requality.core.storage.ISectionModel;

public class UUIDsCacheMan{
  private ICacheStorageProvider ats;
  private final static String UUID_MODEL_VERSION="1.2.1";
  public final static String UUIDS_SECTION_NAME = "UUIDsPathCache";
  private static final String SQL_REMOVE_BY_UID = "DELETE FROM "+UUIDS_SECTION_NAME+" WHERE "+
    FNames.NODE+" = ?;";
  private static final String SQL_SELECT_PATH_BY_UID = "SELECT * FROM "+UUIDS_SECTION_NAME+" WHERE "+
      FNames.NODE+" = ?;";
  private static final String SQL_SET_PATH_BY_UID = "INSERT OR REPLACE INTO "+
      UUIDS_SECTION_NAME+" ("+FNames.NODE+", "+FNames.VALUE+
      ") VALUES (?, ?);";
  private static final String SQL_REMOVE_ALL = "DELETE FROM "+
      UUIDS_SECTION_NAME+";";
  private static final String SQL_SELECT_ALL = "SELECT * FROM "+UUIDS_SECTION_NAME+";";
  private static ISectionModel uuidsModel = new ISectionModel(){
    @Override
    public String getSectionName() {
      return UUIDS_SECTION_NAME;
    }

    private Map<String, CType> definition = new HashMap<>();
    
    private Set<String> idFields = new HashSet<>();
    {
      definition.put(FNames.NODE, CType.STRING);
      definition.put(FNames.VALUE, CType.STRING);
      idFields.add(FNames.NODE);
      idFields.add(FNames.VALUE);
    }

    @Override
    public String getVersion() {
      return UUID_MODEL_VERSION;
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
      return Short.MAX_VALUE-1;//almost urgent
    }
  };
  public static ISectionModel getUUIDsCacheModel() {
    return uuidsModel;
  }
  public UUIDsCacheMan(ICacheStorageProvider cs) {
    ats = cs;
  }
  public CacheStorage getStorage(){
    return ats.getCacheStorage(null);
  }
  public void removeUUID(UUID uid, long session) {
    getStorage().writeLocked(session, UUIDS_SECTION_NAME, SQL_REMOVE_BY_UID, uid);
  }
  public String getPath(UUID uid, long session) {
    Pair<PreparedStatement, ResultSet> p = getStorage().readLocked(session, UUIDS_SECTION_NAME, SQL_SELECT_PATH_BY_UID, uid);
    if(p == null)
      return null;
    ResultSet rs = p.getSecond();
    try{
      if(rs!=null && rs.next())
        return rs.getString(FNames.VALUE);
    } catch (SQLException e) {
      e.printStackTrace();
    }finally{
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
    return null;
  }
  public void dropAll() {
    getStorage().writeLocked(UUIDS_SECTION_NAME, SQL_REMOVE_ALL);
    
  }
  public void setPath(UUID uuid, String path, long session) {
    getStorage().writeLocked(session, UUIDS_SECTION_NAME, SQL_SET_PATH_BY_UID, uuid, path);
  }
  public void setUUIDs(String name, Map<UUID, Object> uids, long session) {
    CacheStorage cs = getStorage();
    CacheTransactionDescription tr = cs.startCacheTransaction("Save uuids "+name, CacheStorage.COMMIT_ALL);
    try{
      for(Entry<UUID, Object> en:uids.entrySet())
        setPath(en.getKey(), en.getValue().toString(),session);
    }
    catch(Exception e){
      if(tr != null && tr.error == null) {
        tr.error("Already rolled back");
        cs.rollback(tr);
      }
      e.printStackTrace();
    }finally {
      if(tr != null && tr.error == null)
        cs.commit(tr);
    }
  }
  public void forEachRecord(Consumer<Pair<String, Object>> one) {
    Pair<PreparedStatement, ResultSet> p = getStorage().readLocked(UUIDS_SECTION_NAME, SQL_SELECT_ALL, CacheStorage.empty);
    if(p == null)
      return;
    ResultSet rs = p.getSecond();
    try{
	  StreamSupport.stream(new Spliterators.AbstractSpliterator<Pair<String, Object>>(
        Long.MAX_VALUE,Spliterator.ORDERED) {
        @Override
        public boolean tryAdvance(Consumer<? super Pair<String, Object>> action) {
            try {
                if(!rs.next()) return false;
                action.accept(new Pair<String, Object>(rs.getString(FNames.NODE), rs.getString(FNames.VALUE)));
                return true;
            } catch(SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
      }, false).onClose(new Runnable() {
        @Override
        public void run() {

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
	  }).forEach(one);
    }finally{
    }
  }
  public Map<String, Object> getUUIDsMap() {
    Map<String,Object> ret = null;
    Pair<PreparedStatement, ResultSet> p = getStorage().readLocked(UUIDS_SECTION_NAME, SQL_SELECT_ALL, CacheStorage.empty);
    if(p == null)
      return Collections.emptyMap();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(ret == null)
          ret = new HashMap<>();
        ret.put(rs.getString(FNames.NODE), rs.getString(FNames.VALUE));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }finally{
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
    return ret!=null?ret:Collections.emptyMap();
  }
  public int getNodesCount() {
    return getUUIDsMap().keySet().size();
  }
}
