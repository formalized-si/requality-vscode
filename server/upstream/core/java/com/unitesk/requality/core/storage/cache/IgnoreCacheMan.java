package com.unitesk.requality.core.storage.cache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.FNames;
import com.unitesk.requality.core.storage.ISectionModel;

public class IgnoreCacheMan extends CacheManager {
  public static final String IGNORE_SECTION_NAME = "IgnoredPath";
  private static String SQL_REM_ALL = "DELETE FROM "+IGNORE_SECTION_NAME+";";
  private static String SQL_GET_IGNORED = "SELECT * FROM "+IGNORE_SECTION_NAME+" WHERE "+FNames.NODE+" = ?;";
  private static String SQL_REM_IGNORED = "DELETE FROM "+IGNORE_SECTION_NAME+" WHERE "+FNames.NODE+" = ?;";
  private static String SQL_ADD_IGNORED = "INSERT OR REPLACE INTO "+IGNORE_SECTION_NAME+"("+FNames.NODE+", "+FNames.VALUE+") VALUES (?, ?);";
  public IgnoreCacheMan(TreeDB db, boolean isCP) {
    super(db,isCP);
  }
  private static ISectionModel ignoreSection = new ISectionModel(){
    private static final String MODEL_VERSION="1.2.2";
    private Map<String, CType> def = new HashMap<>();
    private Set<String> id = new HashSet<>();
    {
      def.put(FNames.NODE, CType.STRING);
      def.put(FNames.VALUE, CType.BLOB);
      id.add(FNames.NODE);
    }
    @Override
    public String getVersion() {
      return MODEL_VERSION;
    }
    @Override
    public String getSectionName() {
      return IGNORE_SECTION_NAME;
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
      return null;
    }

    @Override
    public short getPriority() {
      return LOW;
    }
  };
  public static ISectionModel getIgnoreCacheModel(){
    return ignoreSection;
  }
  public Boolean getIgnored(TreeNode nde) {
    Pair<PreparedStatement, ResultSet> p = getStorage(nde.getTreeDB()).readLocked(IGNORE_SECTION_NAME, SQL_GET_IGNORED, nde.getSUUId());
    if(p == null)
      return null;
    ResultSet rs = p.getSecond();
    try{
      if(!rs.next())
        return null;
      return Boolean.parseBoolean(rs.getString(FNames.VALUE));
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
    return null;
  }
  public void setIgnored(TreeNode node, boolean ig) {
    getStorage(node.getTreeDB()).writeLocked(IGNORE_SECTION_NAME, SQL_ADD_IGNORED, node.getSUUId(), Boolean.toString(ig));
  }
  public void removeAll(TreeDB db){
    getStorage(db).writeLocked(IGNORE_SECTION_NAME, SQL_REM_ALL);
  }
}
