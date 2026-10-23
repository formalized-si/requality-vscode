package com.unitesk.requality.core.storage.cache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.FNames;
import com.unitesk.requality.core.storage.ISectionModel;

public class NamesCacheMan extends CacheManager {
  public static final String NAMES_SECTION_NAME="NamesCache";
  private static final String NAME_MODEL_VERSION="1.2.2";
  private static final String SELECT_NODE_NAME = "SELECT "+FNames.VALUE+" FROM "+
        NAMES_SECTION_NAME+" WHERE "+FNames.NODE+" = ?;";
  private static final String DELETE_NODE_NAME = "DELETE FROM "+
        NAMES_SECTION_NAME+" WHERE "+FNames.NODE+" = ?;";
  private static final String DELETE_ALL = "DELETE FROM "+
      NAMES_SECTION_NAME+";";
  private static final String UPDATE_NODE_NAME = "INSERT OR REPLACE INTO "+
        NAMES_SECTION_NAME+" ("+FNames.NODE+", "+FNames.VALUE+
        ") VALUES (?, ?);";
  private static final String SELECT_NODES_BY_NAME = "SELECT "+ FNames.NODE+
        " FROM "+NAMES_SECTION_NAME+" WHERE "+FNames.VALUE+" = ?;";
  private static final String SELECT_ALL_NAME_RECORDS = "SELECT "+ FNames.NODE+
		  ", "+ FNames.VALUE+" FROM "+NAMES_SECTION_NAME+";";
  private static ISectionModel namesModel = new ISectionModel(){
    @Override
    public String getSectionName() {
      return NAMES_SECTION_NAME;
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
      return NAME_MODEL_VERSION;
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
      return HIGH;
    }
  };
  public static ISectionModel getNamesCacheModel(){
    return namesModel;
  }
  public NamesCacheMan(ICacheStorageProvider rt, boolean isCP) {
    super(rt, isCP);
  }
  public CacheStorage getStorage(){
    return getStorage(null);
  }
  public Object getName(UUID uid) {
    Pair<PreparedStatement, ResultSet> p = getStorage().readLocked(NAMES_SECTION_NAME, SELECT_NODE_NAME, uid.toString());
    if(p == null)
      return null;
    ResultSet rs = p.getSecond();
    try{
      if(rs.next()){
        return rs.getString(1);
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
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
    return null;
  }
  public void deleteName(UUID uid) {
    getStorage().writeLocked(NAMES_SECTION_NAME, DELETE_NODE_NAME, uid.toString());
  }
  
  public void setName(UUID uid, String name) {
    deleteName(uid);
    if(name != null)
      getStorage().writeLocked(NAMES_SECTION_NAME, UPDATE_NODE_NAME, uid.toString(), name);
  }
  public Map<String, Set<UUID>> getAllNamesRecords(){
	Map<String, Set<UUID>> ret = Collections.emptyMap();

    Pair<PreparedStatement, ResultSet> p = getStorage().readLocked(NAMES_SECTION_NAME, SELECT_ALL_NAME_RECORDS);
    Set<UUID> set = null;
    if(p == null)
      return ret;
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(ret.isEmpty())
          ret = new HashMap<>();
        String name = rs.getString(FNames.VALUE);
        set = ret.get(name);
        if(set == null) {
          set = new HashSet<>();
          ret.put(name, set);
        }
        set.add(UUID.fromString(rs.getString(FNames.NODE)));
      }
    } 
    catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return ret; 
  }
  public Set<UUID> getByName(String nodeId) {
    if(nodeId==null || nodeId.isEmpty())
      return Collections.emptySet();
    Pair<PreparedStatement, ResultSet> p = getStorage().readLocked(NAMES_SECTION_NAME, SELECT_NODES_BY_NAME, nodeId);
    Set<UUID> set = null;
    if(p == null)
      return Collections.emptySet();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(set == null)
          set = new HashSet<>();
        set.add(UUID.fromString(rs.getString(FNames.NODE)));
      }
    } 
    catch (SQLException e) {
      e.printStackTrace();
    }
    finally{
      try {
        rs.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return set;
  }
  public void removeAll(TreeDB db){
    getStorage(db).writeLocked(NAMES_SECTION_NAME, DELETE_ALL);
  }
  
}
