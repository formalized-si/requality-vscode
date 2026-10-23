package com.unitesk.requality.core.api;

import java.util.UUID;
import com.unitesk.requality.core.attribute.valueprovider.IGenericValueProvider;
import com.unitesk.requality.core.model.TreeDB;

public interface IValueProviderProvider {
  public IGenericValueProvider getProvider (TreeDB db, UUID uid, String key);
}
