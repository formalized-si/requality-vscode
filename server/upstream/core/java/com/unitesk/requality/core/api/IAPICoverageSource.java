package com.unitesk.requality.core.api;

import java.util.Map;
import com.unitesk.requality.core.descriptors.CoverageStorageSourceDescriptor;
@ApiExtension
public interface IAPICoverageSource {

  public static String COVERAGE_FILE_ID = "com.unitesk.requality.defaultsource";
  Map<String, CoverageStorageSourceDescriptor> getCoverageSources();
}