package com.unitesk.requality.vscode.ui.jface.preference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.core.ServerContext;

/**
 * {@code org.eclipse.jface.preference.PreferenceStore}, persisted as a properties file in the server storage folder.
 * {@link #getPlugin()} is the store of the Requality plug-in (Eclipse {@code Activator.getPreferenceStore()}).
 */
public class PreferenceStore implements IPreferenceStore {
  private static PreferenceStore plugin;
  private final Properties values = new Properties();
  private final Properties defaults = new Properties();
  private final List<IPropertyChangeListener> listeners = new CopyOnWriteArrayList<>();
  private final File file;
  private boolean dirty;

  public PreferenceStore() {
    this(null);
  }

  public PreferenceStore(File file) {
    this.file = file;
    if (file != null && file.isFile()) {
      try (InputStream in = new FileInputStream(file)) {
        values.load(in);
      } catch (IOException e) {
        // starts empty
      }
    }
  }

  /** The Requality plug-in preference store. */
  public static synchronized PreferenceStore getPlugin() {
    if (plugin == null) {
      File dir = ServerContext.server() != null ? ServerContext.server().options().storage : new File(".");
      plugin = new PreferenceStore(new File(dir, "requality.prefs"));
    }
    return plugin;
  }

  @Override
  public synchronized boolean contains(String name) {
    return values.containsKey(name) || defaults.containsKey(name);
  }

  private synchronized String raw(String name) {
    String v = values.getProperty(name);
    return v != null ? v : defaults.getProperty(name);
  }

  @Override
  public boolean getBoolean(String name) {
    return Boolean.parseBoolean(raw(name));
  }

  @Override
  public int getInt(String name) {
    try {
      String v = raw(name);
      return v == null ? 0 : Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public long getLong(String name) {
    try {
      String v = raw(name);
      return v == null ? 0 : Long.parseLong(v.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public double getDouble(String name) {
    try {
      String v = raw(name);
      return v == null ? 0 : Double.parseDouble(v.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public String getString(String name) {
    String v = raw(name);
    return v == null ? "" : v;
  }

  @Override
  public synchronized boolean getDefaultBoolean(String name) {
    return Boolean.parseBoolean(defaults.getProperty(name));
  }

  @Override
  public synchronized int getDefaultInt(String name) {
    try {
      return Integer.parseInt(defaults.getProperty(name, "0"));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public synchronized String getDefaultString(String name) {
    return defaults.getProperty(name, "");
  }

  @Override
  public synchronized void setDefault(String name, boolean value) {
    defaults.setProperty(name, String.valueOf(value));
  }

  @Override
  public synchronized void setDefault(String name, int value) {
    defaults.setProperty(name, String.valueOf(value));
  }

  @Override
  public synchronized void setDefault(String name, long value) {
    defaults.setProperty(name, String.valueOf(value));
  }

  @Override
  public synchronized void setDefault(String name, String value) {
    defaults.setProperty(name, value == null ? "" : value);
  }

  private void put(String name, String value) {
    String old;
    synchronized (this) {
      old = raw(name);
      if (value.equals(old))
        return;
      values.setProperty(name, value);
      dirty = true;
    }
    save();
    PropertyChangeEvent ev = new PropertyChangeEvent(this, name, old, value);
    for (IPropertyChangeListener l : listeners)
      l.propertyChange(ev);
  }

  @Override
  public void setValue(String name, boolean value) {
    put(name, String.valueOf(value));
  }

  @Override
  public void setValue(String name, int value) {
    put(name, String.valueOf(value));
  }

  @Override
  public void setValue(String name, long value) {
    put(name, String.valueOf(value));
  }

  @Override
  public void setValue(String name, double value) {
    put(name, String.valueOf(value));
  }

  @Override
  public void setValue(String name, String value) {
    put(name, value == null ? "" : value);
  }

  @Override
  public void setToDefault(String name) {
    synchronized (this) {
      values.remove(name);
      dirty = true;
    }
    save();
  }

  @Override
  public synchronized boolean isDefault(String name) {
    return !values.containsKey(name);
  }

  /** The values set in this store (not the defaults), sorted by name: the entries of a preference export. */
  public synchronized java.util.Map<String, String> values() {
    java.util.Map<String, String> r = new java.util.TreeMap<>();
    for (String k : values.stringPropertyNames())
      r.put(k, values.getProperty(k));
    return r;
  }

  @Override
  public synchronized boolean needsSaving() {
    return dirty;
  }

  public synchronized void save() {
    if (file == null)
      return;
    file.getParentFile().mkdirs();
    try (OutputStream out = new FileOutputStream(file)) {
      values.store(out, "Requality preferences");
      dirty = false;
    } catch (IOException e) {
      System.err.println("[prefs] cannot save " + file + ": " + e);
    }
  }

  @Override
  public void addPropertyChangeListener(IPropertyChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removePropertyChangeListener(IPropertyChangeListener listener) {
    listeners.remove(listener);
  }
}
