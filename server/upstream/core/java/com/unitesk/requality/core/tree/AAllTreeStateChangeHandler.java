package com.unitesk.requality.core.tree;

import com.unitesk.requality.core.model.TreeDB;

public abstract class AAllTreeStateChangeHandler implements IStateChangeHandler {
  @Override
  public boolean filterTreeDB(TreeDB db) {
    return true;
  }
}
