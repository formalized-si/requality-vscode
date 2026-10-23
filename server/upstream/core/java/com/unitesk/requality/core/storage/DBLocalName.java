package com.unitesk.requality.core.storage;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.repo.RevisionInfo;

public class DBLocalName {
  public String name;
  public String revision;
  public RevisionInfo rawRevisionPart = null;
  public static DBLocalName formLocalNameKey(TreeDB project, String lname, RevisionInfo ri) {
    DBLocalName name = new DBLocalName();
    name.name = lname;
    name.revision = RevisionInfo.toString(project, ri);
    name.rawRevisionPart = ri;
    return name;
  }
  public static DBLocalName formLocalNameKey(String lname, String string) {
    DBLocalName name = new DBLocalName();
    name.name = lname;
    name.revision = string;
    if(string == null)
      name.revision = "null";
    return name;
  }
  @Override
  public boolean equals(Object obj) {
    if(obj == null || !(obj instanceof DBLocalName))
      return false;
    DBLocalName b = (DBLocalName) obj;
    return name.equals(b.name) && b.revision.equals(revision);
  }
  @Override
  public String toString() {
    return name+"|"+revision;
  }
}
