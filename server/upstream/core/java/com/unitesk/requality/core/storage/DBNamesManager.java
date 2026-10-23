package com.unitesk.requality.core.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.repo.RevisionInfo;

public class DBNamesManager {
  public static String SEPARATOR = "|";
  public static DBNamesManager DEFAULT = new DBNamesManager(); 
  public Map<String, Set<DBLocalName>> names = new HashMap<>();
  public Map<String, String> rvnames = new HashMap<>();
  public void register(DBLocalName ln, String pn) {
    if(!names.containsKey(pn))
      names.put(pn, new HashSet<>());
    names.get(pn).add(ln);
    rvnames.put(ln.toString(), pn);
  }
  public void register(TreeDB db, DBLocalName ln, String pn) {
    if(ln==null&&db!=null)
      ln = formLocalNameKey(db, db.getLocalName(),db.revision);
    if(pn==null&&db!=null)
      pn = db.getProjectName();
    if(ln == null)
    {
      RLogger.logError("Attempt to register null local name");
      return;
    }
    if(!names.containsKey(pn))
      names.put(pn, new HashSet<>());
    names.get(pn).add(ln);
    rvnames.put(ln.toString(), pn);
  }

  
  public void forget(TreeDB db) {
    forget(formLocalNameKey(db.getLocalName(), RevisionInfo.toString(db, db.revision)));
  }
  public void forget(DBLocalName ln) {
    String pn = rvnames.remove(ln.toString());
    if(pn!=null) {
	  Set<DBLocalName> s = names.get(pn);
	  if(s!=null)
	    s.remove(ln);
	  if(s!=null && !s.isEmpty()) 
	    names.remove(pn);
    }
  }
  public Set<DBLocalName> getLocalNames(String s) {
    return names.getOrDefault(s, Collections.emptySet());
  }

  public String getProjectName(DBLocalName s) {
    return rvnames.get(s.toString());
  }
  private static DBLocalName formLocalNameKey(TreeDB db, String lname, RevisionInfo ri) {
    return DBLocalName.formLocalNameKey(db, lname, ri);
  }
  private static DBLocalName formLocalNameKey(String lname, String string) {
    return DBLocalName.formLocalNameKey(lname, string);
  }
  public static DBLocalName formLocalName(String dbName, String localRevisionString) {
    return null;
  }
}
