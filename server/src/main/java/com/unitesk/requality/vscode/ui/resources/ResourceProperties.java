package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.QualifiedName;

/**
 * The persistent properties of resources ({@code IResource.getPersistentProperty}, kept in the Eclipse workspace
 * metadata): a JSON store next to the workspace state, keyed by the location of the resource. Without a file (no
 * storage) the properties live in memory for the session.
 */
public final class ResourceProperties {
  /** The name of the store file, in the folder of the workspace state. */
  public static final String FILE_NAME = "requality-resource-properties.json";

  private static File file;
  /** location → (qualified name → value). */
  private static final Map<String, Map<String, String>> VALUES = new LinkedHashMap<>();

  private ResourceProperties() {
  }

  /** Sets the store file (null: in memory) and loads it. */
  public static synchronized void setFile(File stateFile) {
    file = stateFile == null || stateFile.getParentFile() == null ? null
        : new File(stateFile.getParentFile(), FILE_NAME);
    VALUES.clear();
    if (file == null || !file.isFile())
      return;
    try {
      JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
          .getAsJsonObject();
      for (Map.Entry<String, JsonElement> e : o.entrySet()) {
        Map<String, String> props = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> p : e.getValue().getAsJsonObject().entrySet())
          props.put(p.getKey(), p.getValue().getAsString());
        VALUES.put(e.getKey(), props);
      }
    } catch (RuntimeException | IOException e) {
      RLogger.logInfo("Cannot read the resource properties " + file + ": " + e);
    }
  }

  /** The value of the property of the resource at the location, or null. */
  public static synchronized String get(String location, QualifiedName name) {
    Map<String, String> props = VALUES.get(location);
    return props == null ? null : props.get(name.toString());
  }

  /** Sets (or with a null value removes) the property of the resource at the location and saves the store. */
  public static synchronized void set(String location, QualifiedName name, String value) {
    Map<String, String> props = VALUES.get(location);
    if (value == null) {
      if (props == null || props.remove(name.toString()) == null)
        return;
      if (props.isEmpty())
        VALUES.remove(location);
    } else {
      if (props == null)
        VALUES.put(location, props = new LinkedHashMap<>());
      props.put(name.toString(), value);
    }
    save();
  }

  /**
   * The resource at {@code from} (and its members) moved to {@code to}: the properties follow it, as
   * {@code IResource.move} keeps them; with {@code keep} (a copy) the originals keep theirs too.
   */
  public static synchronized void moved(File from, File to, boolean keep) {
    if (from == null || to == null)
      return;
    String f = from.getAbsolutePath();
    String t = to.getAbsolutePath();
    Map<String, Map<String, String>> changed = new LinkedHashMap<>();
    for (java.util.Iterator<Map.Entry<String, Map<String, String>>> it = VALUES.entrySet().iterator(); it.hasNext();) {
      Map.Entry<String, Map<String, String>> e = it.next();
      String k = e.getKey();
      if (!k.equals(f) && !k.startsWith(f + File.separator))
        continue;
      changed.put(t + k.substring(f.length()), new LinkedHashMap<>(e.getValue()));
      if (!keep)
        it.remove();
    }
    if (changed.isEmpty())
      return;
    VALUES.putAll(changed);
    save();
  }

  /** The resource at the location (and its members) was deleted: so are its properties. */
  public static synchronized void deleted(File file) {
    if (file == null)
      return;
    String f = file.getAbsolutePath();
    if (VALUES.keySet().removeIf(k -> k.equals(f) || k.startsWith(f + File.separator)))
      save();
  }

  private static void save() {
    if (file == null)
      return;
    JsonObject o = new JsonObject();
    for (Map.Entry<String, Map<String, String>> e : VALUES.entrySet()) {
      JsonObject p = new JsonObject();
      e.getValue().forEach(p::addProperty);
      o.add(e.getKey(), p);
    }
    try {
      File dir = file.getAbsoluteFile().getParentFile();
      if (dir != null)
        dir.mkdirs();
      File tmp = new File(dir, file.getName() + ".tmp");
      Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
      FileMoves.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      RLogger.logError(e);
    }
  }
}
