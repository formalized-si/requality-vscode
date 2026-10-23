package com.unitesk.requality.core.api;

import java.util.List;
@ApiExtension
public interface IAPIStoredVersionSource {

  List<IStoredVersionProvider> getStoredversionProviders();
}