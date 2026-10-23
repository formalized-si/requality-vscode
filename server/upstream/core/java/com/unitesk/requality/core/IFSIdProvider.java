package com.unitesk.requality.core;

import com.unitesk.requality.core.selection.FSId;

public interface IFSIdProvider {
  public FSId getFSId();
  public boolean isSelectionSource();
}
