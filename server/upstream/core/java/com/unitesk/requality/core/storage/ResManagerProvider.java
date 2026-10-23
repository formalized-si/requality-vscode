package com.unitesk.requality.core.storage;

import com.unitesk.requality.core.model.Pair;

public abstract class ResManagerProvider {
  public abstract IResTracker createTracker(Pair<String, Object>... params);
  public IResTracker initTracker(Pair<String, Object>... params) {
    IResTracker one = createTracker(params);
    if(one!=null)
      one.init(params);
    return one;
  }
}
