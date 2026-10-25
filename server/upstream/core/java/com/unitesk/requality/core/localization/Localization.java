package com.unitesk.requality.core.localization;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.log.RLogger;

public class Localization {
  private static final String BUNDLE_EN =
    "localization.localization_en"; //$NON-NLS-1$
  private static final String BUNDLE_RU =
    "localization.localization_ru"; //$NON-NLS-1$
  private static ReadWriteLock rwl = new ReentrantReadWriteLock();
  private static Lock wl = rwl.writeLock();
  private static List<ResourceBundle> activeLocalizationBundle =
     null;
  private static Map<Locale, List<ResourceBundle>> localizationBundles = new HashMap<>();
  private static ConcurrentHashMap<String, String> stringCache = new ConcurrentHashMap<>();
  private static Locale activeLocale = null;
  private static Localization one = new Localization();//for report calls
  public static Locale getLocale() {
    Lock rl = rwl.readLock();
    rl.lock();
    try {
      activeLocale = Locale.getDefault();
    }finally {
      rl.unlock();
    }
    if(!localizationBundles.containsKey(activeLocale)) {
      wl.lock();
      if(!localizationBundles.containsKey(activeLocale))
        activeLocale = Locale.ENGLISH;
      wl.unlock();
    }
	return activeLocale;
  }
  public static String getLanguage() {
    return getLocale().getLanguage().toLowerCase();
  }
  public static List<ResourceBundle> getResourceBundle() {
    boolean bundleState = false;
    Lock rl = rwl.readLock();
    rl.lock();
    try {
      bundleState = activeLocalizationBundle == null;
    }finally {
      rl.unlock();
    }
    if(bundleState) {
      wl.lock();
      try {
        if(activeLocalizationBundle == null) {
          //default set, maybe move to main api source as an example
          registerResourcesBundle(Locale.ENGLISH, ResourceBundle.getBundle(BUNDLE_EN));
          Locale ruLoc = Locale.getDefault();
          if(!ruLoc.getLanguage().equals("ru")) {
            for(Locale l : Locale.getAvailableLocales())
              if(l.getLanguage().equals("ru")) {
                ruLoc = l;
                break;
              }
          }
          registerResourcesBundle(ruLoc, ResourceBundle.getBundle(BUNDLE_RU));
          activeLocalizationBundle = localizationBundles.get(getLocale());
          if(activeLocalizationBundle == null)
            activeLocalizationBundle = localizationBundles.get(Locale.ENGLISH);
        }
      }finally {
        wl.unlock();
      }
    }
    return activeLocalizationBundle;
  }
  public static void registerResourcesBundle(Locale locale, ResourceBundle bundle) {
    List<ResourceBundle> resList = null;
    Lock rl = rwl.readLock();
    rl.lock();
    try {
      resList = localizationBundles.get(locale);
    }finally {
      rl.unlock();
    }
    if(resList == null)
    {
      wl.lock();
      try {
        resList = localizationBundles.get(locale); 
        if(resList == null) {
          resList = new LinkedList<>();
          localizationBundles.put(locale, resList);
        }
      }finally {
        wl.unlock();
      }
    }
    wl.lock();
    try {
      resList.add(bundle);
      if(getLocale() == locale)
        activeLocalizationBundle = resList;
    }finally{
      wl.unlock();
    }
    
  }
  private static String getStoredString(StringsSource source, String id) {
    if(getResourceBundle() == null) {
      return null;
    }
    try {
      String key = source.name().toLowerCase()+"."+id;
      String cached = stringCache.get(key);
      if(cached == null) {
        synchronized (stringCache) {
          cached = stringCache.get(key);
          if(cached == null) {
            String res = null; 
            for(ResourceBundle rb:activeLocalizationBundle) { 
              res = rb.getString(key);
              if(res!=null)
                break;
            }
            cached = res;
          }
        }
      }
      return cached;
    }catch(MissingResourceException mre) {
      String bname = getBundleNames(); 
      RLogger.logDebug(String.format("try to get %s.%s from %s in locale %s but resource is missing", source.name(),id,bname, activeLocale.toString()));
    }catch(ClassCastException cce) {
      String bname = getBundleNames(); 
      RLogger.logDebug(String.format("try to get %s.%s from %s in locale %s but found resource is not a string", source.name(),id,bname, activeLocale.toString()));
    }
    return null;
  }
  public static String getLocalizedString(StringsSource source, String id, String defValue, String[] data) {
    String val = null;
    val = getStoredString(source, id);
    if(val == null)
      val = defValue;
    if(val == null) {
      String bname = getBundleNames(); 
      throw new RuntimeException("Tries to get localized value but it cannot be found and def value is not provided "+source.name()+"."+id+" on "+bname+"("+activeLocale.toString()+")");
    }
    return StringMatcher.replaceBraces(activeLocale, val, data);
  }
  private static String getBundleNames() {
    StringBuilder sb = new StringBuilder();
    for(ResourceBundle rb: getResourceBundle())
    {
      if(!sb.isEmpty())
        sb.append(", ");
      sb.append(rb.getBaseBundleName());
    }
    return sb.toString();
  }
  public static Localization getOne() {
	return one;
  }
}
