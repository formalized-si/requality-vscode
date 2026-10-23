package com.unitesk.requality.repo;

import java.util.Map;

public class RepoTreeState<T,V>{
  public String revision;
  public T storage;
  public Map<V,String> retRevisions;
  public RepoTreeState(String rev, T storage, Map<V, String> revs){
    this.revision = rev;
    retRevisions = revs;
    this.storage = storage;
  }
}