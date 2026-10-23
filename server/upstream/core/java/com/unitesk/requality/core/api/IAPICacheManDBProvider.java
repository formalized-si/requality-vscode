package com.unitesk.requality.core.api;

import java.util.Map;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.cache.CacheManipulator;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
@ApiExtension
public interface IAPICacheManDBProvider extends ILightAPISource{

  Map<String, CacheManipulator<TreeDB>> getAPIDBPersistCacheManipulator(
      TreeDB treeDB, SettingsManipulator sm);

  Map<String, CacheManipulator<TreeDB>> getAPIDBCacheManipulator(
      TreeDB treeDB, SettingsManipulator sm);
  
}
