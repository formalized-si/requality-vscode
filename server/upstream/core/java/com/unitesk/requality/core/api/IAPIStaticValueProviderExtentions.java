package com.unitesk.requality.core.api;

import java.util.Map;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.model.Pair;
@ApiExtension
public interface IAPIStaticValueProviderExtentions {
  public Map<String,Pair<IValueProvider,Boolean>> getStaticValueProviders();
}
