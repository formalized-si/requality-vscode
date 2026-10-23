package com.unitesk.requality.core.storage.formatversion;

import java.util.Queue;

public class VersionUpdateInfo {
  private String oldVersionmin, oldVersionmax, newVersion, status, groupId;
  private Queue<IFormatVersionConverter> converter;

  public VersionUpdateInfo(
      String groupId,
      String status,
      Queue<IFormatVersionConverter> convPath,
      String oldVersionmin,
      String oldVersionmax,
      String newVersion) {
    this.status = status;
    this.converter = convPath;
    this.groupId = groupId;
    this.oldVersionmin = oldVersionmin;
    this.oldVersionmax = oldVersionmax;
    this.newVersion = newVersion;
  }
  public String getStatus() {
    return status;
  }

  public Queue<IFormatVersionConverter> getConverters() {
    return converter;
  }

  @Override
  public String toString() {
    return "data format '" + groupId + "' ([" + oldVersionmin + ".."+oldVersionmax+"] -> " + newVersion + ") " + status;
  }

  public String getGroupId() {
    return groupId;
  }
}
