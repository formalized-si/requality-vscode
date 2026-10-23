package com.unitesk.requality.core.api;

import java.util.ArrayList;
import java.util.List;
import com.unitesk.requality.core.storage.formatversion.IFormatVersionConverter;
import com.unitesk.requality.core.storage.formatversion.Requality_old_0213;

public class RequalityStoredVersionProvider implements IStoredVersionProvider{
  private static String reqVersion = "com.unitesk.nodegroup.requality";
  private static String currentReqVersion = "0.21.3";

  @Override
  public List<IFormatVersionConverter> getStoredversionConverters() {
    List<IFormatVersionConverter> convs = new ArrayList<>();
    convs.add(new Requality_old_0213());
    return convs;
  }

  public static String getVersion(){
    return currentReqVersion;
  }
  
  @Override
  public String getGroupName() {
    return reqVersion;
  }
  @Override
  public String getCurrentVersion() {
    return currentReqVersion;
  }

  @Override
  public int getPriority() {
    return -1;//high priority
  }
}
