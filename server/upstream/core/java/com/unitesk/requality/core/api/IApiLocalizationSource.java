package com.unitesk.requality.core.api;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public interface IApiLocalizationSource {
  public Map<Locale, ResourceBundle> getLocalizations();
}
