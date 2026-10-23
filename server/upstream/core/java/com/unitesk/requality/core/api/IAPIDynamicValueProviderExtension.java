package com.unitesk.requality.core.api;

import java.util.Map;
import com.unitesk.requality.core.model.Pair;
@ApiExtension
public interface IAPIDynamicValueProviderExtension {
  public Map<String, Pair<IValueProviderProvider, Boolean>> getDynamicValueProviders();
}
