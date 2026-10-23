package com.unitesk.requality.core.attribute.reference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBLocalName;
import com.unitesk.requality.core.storage.DBNamesManager;

public class RelationsTreeContext {
  public Set<TreeDB> allRelated = new HashSet<>();
  public Set<String> allRelatedNames = new HashSet<>();
  public Map<String, TreeRelationsCounter> countLinkForward = new HashMap<>();
  public Map<String, TreeRelationsCounter> countLinkBackward = new HashMap<>();
  private DBNamesManager names = new DBNamesManager();
  public void addLinksInfo(String dbLocalName, TreeRelationsCounter linksForward, TreeRelationsCounter linksBackward) {
    TreeRelationsCounter forw = countLinkForward.get(dbLocalName);
    TreeRelationsCounter back = countLinkBackward.get(dbLocalName);
    if(forw == null)
      forw = linksForward;
    else
      forw = forw.merge(linksForward);
    if(back == null)
      back = linksBackward;
    else
      back = back.merge(linksBackward);
    countLinkForward.put(dbLocalName, forw);
    countLinkBackward.put(dbLocalName, back);
  }
  public void registerName(DBLocalName dbname, String projectName) {
    names.register(dbname, projectName);
  }
  public String getProjectName(DBLocalName lname) {
    return names.getProjectName(lname);
  }
}
