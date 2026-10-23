package com.unitesk.requality.repo;

import java.net.URI;

import com.unitesk.requality.core.model.TreeDB;

public interface IRepoMapper{
  public String getRepoProviderId(URI project);
  public String getRepoProviderId(TreeDB project);
}
