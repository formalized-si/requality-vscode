package com.unitesk.requality.core.checker;

import com.unitesk.requality.core.localization.StringsSource;

public interface ILocalizedStringSource {
  public String getLocalizedString();
  public StringsSource getLocalizationCategory();
}
