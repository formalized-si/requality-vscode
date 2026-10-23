package com.unitesk.requality.core.api;

import java.util.List;
import com.unitesk.requality.core.storage.formatversion.IFormatVersionConverter;

public abstract interface IStoredVersionProvider {

  public List<IFormatVersionConverter> getStoredversionConverters();
  public String getGroupName();
  public String getCurrentVersion();
  public int getPriority();
}
