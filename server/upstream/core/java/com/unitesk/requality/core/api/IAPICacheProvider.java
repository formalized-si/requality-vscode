package com.unitesk.requality.core.api;

import java.util.List;
import java.util.Map;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.ISectionModel;
@ApiExtension
public interface IAPICacheProvider {
  public static final String DEFAULT = "default";
  public void clearCaches(TreeDB db, boolean delete);
  public Map<String, List<ISectionModel>> getSectionModels(
      boolean isLocal);
}
