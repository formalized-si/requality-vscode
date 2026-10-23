package com.unitesk.requality.core.descriptors;

import com.unitesk.requality.tests.ICoverageStorageSource;

public class CoverageStorageSourceDescriptor
    implements Comparable<CoverageStorageSourceDescriptor> {
  public static final String PREFIX = "_cov";
  private String name;
  private String id;
  private String setts;
  private ICoverageStorageSource storage;
  

  public CoverageStorageSourceDescriptor(String id, String name, ICoverageStorageSource st) {
    this.id = id;
    this.name = name;
    this.storage = st;
    this.setts = st.getStoredDescription();
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public ICoverageStorageSource getStorageInput() {
    return storage;
  }

  @Override
  public int compareTo(CoverageStorageSourceDescriptor o) {
    return name.compareTo(o.getName());
  }

  public String getSettingsDescription() {
	return setts;
  }
}
