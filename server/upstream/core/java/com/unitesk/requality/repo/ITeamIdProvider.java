package com.unitesk.requality.repo;

import com.unitesk.requality.core.model.TreeDB;

public interface ITeamIdProvider {
  public String getTeamId(TreeDB project);
}
