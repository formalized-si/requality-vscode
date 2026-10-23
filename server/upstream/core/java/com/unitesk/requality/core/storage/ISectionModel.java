package com.unitesk.requality.core.storage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.unitesk.requality.core.storage.cache.CType;

public interface ISectionModel {
  static short LOW = -10, NORMAL = 0, HIGH = 10;
  String getSectionName();
  //Map of fieldName -> type
  Map<String, CType> getSectionDefinition();
  Set<String> getIdFields(); 
  Set<String> getAutoIncrementedFields();
  List<String> getTriggers();
  String getVersion();
  //void reactProjectDeleted(TreeDB db, IResTracker tracker, String projectName, CacheStorage cs);
  short getPriority();
}
