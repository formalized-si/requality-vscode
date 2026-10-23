package com.unitesk.requality.core.localization;

import java.util.Locale;

public class StringMatcher {
  private static String[] EMPTY = new String[0];
  public static String replaceBraces(Locale locale, String string, String[] data) {
    return String.format(locale, string, data == null ? EMPTY : data);
  }
}
