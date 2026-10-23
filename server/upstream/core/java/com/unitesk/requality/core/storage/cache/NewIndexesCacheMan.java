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

public class NewIndexesCacheMan extends CacheManager {
  public static final String NEW_IND_SECTION_NAME = "NewIndexesCache";
  private static final String SQL_REMOVE_ALL = "DELETE FROM "+NEW_IND_SECTION_NAME+";";
  private static final String SQL_REMOVE_IND = "DELETE FROM "+NEW_IND_SECTION_NAME+" WHERE "+FNames.NODE+"=?;";
  private static final String SQL_ADD_IND= "INSERT INTO "+NEW_IND_SECTION_NAME+"("+FNames.NODE+") VALUES (?);";
  private static final String SQL_GET = "SELECT * FROM "+NEW_IND_SECTION_NAME+";";
  public static final String INITED_PREFIX = "newIndexesInited";
  public NewIndexesCacheMan(TreeDB db,boolean isCP) {
    super(db,isCP);
    // TODO Auto-generated constructor stub
  }
  private static ISectionModel locSection = new ISectionModel(){
    private static final String MODEL_VERSION="1.2.1";
    private Set<String> id = new HashSet<>();
    private Set<String> sort = new HashSet<>();
    private Map<String, CType> def = new HashMap<>();
    {
      def.put(FNames.NODE, CType.STRING);
      id.add(FNames.NODE);
    }
    @Override
    public String getVersion() {
      return MODEL_VERSION;
    }
    @Override
    public String getSectionName() {
      return NEW_IND_SECTION_NAME;
    }
  
    @Override
    public Map<String, CType> getSectionDefinition() {
      return def;
    }
  
    @Override
    public Set<String> getIdFields() {
      return id;
    }
  
    @Override
    public Set<String> getAutoIncrementedFields() {
      return null;
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
  public static ISectionModel getIndexCacheSection(){
    return locSection;
  }
  void add(TreeDB db, UUID val){
    getStorage(db).writeLocked(NEW_IND_SECTION_NAME, SQL_ADD_IND, val.toString());
  }
  void remove(TreeDB db, UUID val){
    getStorage(db).writeLocked(NEW_IND_SECTION_NAME, SQL_REMOVE_IND, val.toString());
  }
  void removeAll(TreeDB db){
    getStorage(db).writeLocked(NEW_IND_SECTION_NAME, SQL_REMOVE_ALL);
  }
  public Set<UUID> getAll(){
    Set<UUID> ret = null;
    Pair<PreparedStatement, ResultSet> p = getStorage(null).readLocked(NEW_IND_SECTION_NAME, SQL_GET);
    if(p == null)
      return Collections.emptySet();
    ResultSet rs = p.getSecond();
    try{
      while(rs.next()){
        if(ret == null)
          ret = new HashSet<>();
        ret.add(UUID.fromString(rs.getString(FNames.NODE)));
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
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
    if(ret!=null)
      return ret;
    return Collections.emptySet();
  }
}
