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

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.storage.FNames;
import com.unitesk.requality.core.storage.ISectionModel;

public class ProjSettsCacheMan extends CacheManager{
  public static final String PROJECT_SETTINGS_CACHE_SECTION_NAME = "Settings";
  private static final String PROJ_MODEL_VERSION="1.2.2";
  private static String SQL_READ = "SELECT * FROM "+PROJECT_SETTINGS_CACHE_SECTION_NAME+" WHERE "+FNames.KEY
      + " = ?;";
  private static String SQL_SET = "INSERT OR REPLACE INTO "+PROJECT_SETTINGS_CACHE_SECTION_NAME+" ("+FNames.KEY
      + ", "+FNames.VALUE+") VALUES (?,?);";
  private static String SQL_REMOVE = "DELETE FROM "+PROJECT_SETTINGS_CACHE_SECTION_NAME+" WHERE "+FNames.KEY
      + " = ?;";
  private static String SQL_REMOVE_ALL = "DELETE FROM "+PROJECT_SETTINGS_CACHE_SECTION_NAME+";";
  public ProjSettsCacheMan(ICacheStorageProvider cs, boolean isCP) {
    super(cs, isCP);
  }
  public CacheStorage getStorage(){
    return getStorage(null);
  }
  public void dropProjectSetting(String name){
    getStorage().writeLocked(PROJECT_SETTINGS_CACHE_SECTION_NAME, SQL_REMOVE, name);
  }
  public void setProjectSetting(String name, Object value){
    if(value == null || value == Boolean.FALSE || value.equals("0"))
      dropProjectSetting(name);
    else
      getStorage().writeLocked(PROJECT_SETTINGS_CACHE_SECTION_NAME, SQL_SET, name,value);
  }
  public Object getProjectSetting(String name){
    ResultSet rs = null;
    Pair<PreparedStatement, ResultSet> p = null; 
    try{
      CacheStorage cs = getStorage();
      if(cs == null)
        return null;
      p = cs.readLocked(PROJECT_SETTINGS_CACHE_SECTION_NAME, SQL_READ, name);
      if(p == null)
        return null;
      rs = p.getSecond();
      if(rs!=null && rs.next())
        return rs.getString(FNames.VALUE);
    } catch (SQLException e) {
      // TODO Auto-generated catch block
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
        if(p!=null)
          p.getFirst().close();
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }
  private static ISectionModel settingsModel = new ISectionModel(){
    @Override
    public String getSectionName() {
      return PROJECT_SETTINGS_CACHE_SECTION_NAME;
    }

    private Map<String, CType> definition = new HashMap<>();
    
    private Set<String> idFields = new HashSet<>();
    {
      
      definition.put(FNames.KEY, CType.STRING);
      definition.put(FNames.VALUE, CType.BLOB);
      idFields.add(FNames.KEY);
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
      return Short.MAX_VALUE;//urgent for all caches
    }

    @Override
    public String getVersion() {
      return PROJ_MODEL_VERSION;
    }

  };

  public static ISectionModel getProjectSettingsCacheModel() {
    return settingsModel;
  }
  public void removeAll() {
    getStorage().writeLocked(PROJECT_SETTINGS_CACHE_SECTION_NAME, SQL_REMOVE_ALL);
  }
}
