package com.unitesk.requality.repo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;

public class SliceRevisonInfo extends RevisionInfo {  
  private static String DB_SEPARATOR = "_-_",//more likely not to mentioned in name
              REVISION_SEPARATOR="-_-";
  private Map<TreeDB, RevisionInfo> riMap;
  private String fullRevId = null;

  public SliceRevisonInfo(RevisionInfo ri, HashMap<TreeDB, RevisionInfo> riMap) {
    super(ri == null?null:ri.getRevision(), ri==null?null:ri.getDate());
    this.riMap = Collections.unmodifiableMap(riMap);
    fullRevId = null;
    StringBuilder sb = new StringBuilder();
    String[]fullIdParts = new String[riMap.entrySet().size()];
    int i = 0;
    for(Entry<TreeDB, RevisionInfo> en: riMap.entrySet()) {
      sb.setLength(0); 
      sb.append(en.getKey().getProjectName());
      sb.append(REVISION_SEPARATOR);
      String rev = "";
      if(en.getValue() == null)
        RLogger.logInfo("Problem during loading revision "+(ri==null?"null":ri.toString())+" on "+en.getKey().getSemiLocalName());
      else
        if(en.getValue().getRevision() == null)
          RLogger.logError("Loaded revision is null for "+(ri==null?"null":ri.toString())+" on "+en.getKey().getSemiLocalName());
        else
          rev = en.getValue().getRevision();
      sb.append(rev);
      fullIdParts[i++] = sb.toString();
    }
    Arrays.sort(fullIdParts);
    fullRevId = String.join(DB_SEPARATOR, fullIdParts);
  }
  @Override
  public Date getDate() {
    return super.getDate();
  }
  public Map<TreeDB, RevisionInfo> getDBRevisions() {
    return riMap;
  }
  @Override
  public Date getDate(TreeDB db) {
    RevisionInfo ri = riMap.get(db);
    if(ri == null) {
      for(Entry<TreeDB, RevisionInfo> s:riMap.entrySet())
      if(s.getKey().getLocalName().equals(db.getLocalName()))
          return s.getValue().getDate();
      return super.getDate(db);
    }
    return ri!=null ? 
        (ri.getDate()!=null?ri.getDate():getDate()) 
        : getDate();
  }
  private static Pattern revisionPattern = Pattern.compile(DB_SEPARATOR+"(.*)"+REVISION_SEPARATOR+"([a-zA-Z0-9]{40})");
  public static String getRevision(String pname, String fullId) {
    if(fullId == null)
      return null;
    Matcher m = revisionPattern.matcher(DB_SEPARATOR+fullId);
    int ind = 0;
    while(m.find(ind)) {
      String dbName = m.group(1);
      String rev = m.group(2);
      if(dbName.equals(pname))
        return rev;
      ind = m.start()+1;
    }
    return null;
    
  }
  @Override
  public String getRevision(TreeDB db) {
    if(db == null)
      return "";
    RevisionInfo ri = riMap.get(db);
    if(ri == null)
    {
      for(Entry<TreeDB, RevisionInfo> s:riMap.entrySet())
        if(s.getKey().getLocalName().equals(db.getLocalName()))
          return s.getValue().getRevision();
      return super.getRevision(db);
    }
    if(ri!=null)
    {
      String riString = ri.getRevision(db);
      if(riString!=null)
        return riString;
    }
    return "";
  }
  public String getRevision() {
    return fullRevId;
  }
}
