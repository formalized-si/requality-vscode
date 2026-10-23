package com.unitesk.requality.core.tree;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.repo.RevisionInfo;

public interface IStateChangeHandler {
  void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state);
  public boolean filterTreeDB(TreeDB db);
}
