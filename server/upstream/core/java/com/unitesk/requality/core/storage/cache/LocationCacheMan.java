package com.unitesk.requality.core.storage.cache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.ISectionModel;

public class LocationCacheMan extends CacheManager {
  public static final String LOCATION_SECTION_NAME = "LocationsCache";
  public static final String INITED_PREFIX = "locationsInited";
  public LocationCacheMan(TreeDB db,boolean isCP) {
    super(db,isCP);
    // TODO Auto-generated constructor stub
  }
  private static ISectionModel locSection = new ISectionModel(){
    private static final String MODEL_VERSION="1.2.4";
    private Set<String> id = new HashSet<>();
    private Set<String> sort = new HashSet<>();
    private Map<String, CType> def = new HashMap<>();
    {
    }
    @Override
    public String getVersion() {
      return MODEL_VERSION;
    }
    @Override
    public String getSectionName() {
      return LOCATION_SECTION_NAME;
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
  public static ISectionModel getLocationSection(){
    return locSection;
  }
}
