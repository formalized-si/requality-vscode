package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.Main;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.IPath;

/**
 * The markers of workspace resources (the Eclipse marker manager). Listeners (the server's problem view) are told
 * which resources' markers changed. The markers of the persistent types (bookmarks and tasks, as in Eclipse) are kept
 * in a file of the server storage across restarts, with their ids, attributes and creation times.
 */
public final class MarkerStore {
  /** Marker types of plugin.xml: the Requality problem marker is a problem marker. */
  private static final Map<String, String> SUPER_TYPES = new HashMap<>();
  static {
    SUPER_TYPES.put("com.unitesk.requality.marker", IMarker.PROBLEM);
    SUPER_TYPES.put(IMarker.PROBLEM, IMarker.MARKER);
    SUPER_TYPES.put(IMarker.TASK, IMarker.MARKER);
    SUPER_TYPES.put(IMarker.TEXT, IMarker.MARKER);
    SUPER_TYPES.put(IMarker.BOOKMARK, IMarker.MARKER);
  }

  private static final AtomicLong IDS = new AtomicLong();
  private static final Map<IPath, List<Marker>> MARKERS = new LinkedHashMap<>();
  private static final List<Consumer<List<IResource>>> LISTENERS = new CopyOnWriteArrayList<>();
  /** The file of the persistent markers (guarded by MARKERS); null when they are kept in memory only. */
  private static File file;
  /** The file was read (guarded by MARKERS): it is read once the workspace folders are known. */
  private static boolean loaded;
  /** A persistent marker changed since the file was written. */
  private static volatile boolean persistentChanged;

  private MarkerStore() {
  }

  /** Called with the resources whose markers changed. */
  public static void addListener(Consumer<List<IResource>> l) {
    LISTENERS.add(l);
  }

  public static void removeListener(Consumer<List<IResource>> l) {
    LISTENERS.remove(l);
  }

  static boolean isSubtype(String type, String superType) {
    for (String t = type; t != null; t = SUPER_TYPES.get(t))
      if (t.equals(superType))
        return true;
    return false;
  }

  /** The marker types kept across sessions: bookmarks and tasks (org.eclipse.core.resources, persistent="true"). */
  static boolean isPersistent(String type) {
    return isSubtype(type, IMarker.BOOKMARK) || isSubtype(type, IMarker.TASK);
  }

  private static void changed(IResource r) {
    if (persistentChanged) {
      persistentChanged = false;
      save();
    }
    List<IResource> list = new ArrayList<>();
    list.add(r);
    for (Consumer<List<IResource>> l : LISTENERS) {
      try {
        l.accept(list);
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
    }
  }

  public static IMarker create(IResource resource, String type) {
    ensureLoaded();
    Marker m = new Marker(resource, type);
    synchronized (MARKERS) {
      MARKERS.computeIfAbsent(resource.getFullPath(), k -> new ArrayList<>()).add(m);
    }
    if (isPersistent(type))
      persistentChanged = true;
    changed(resource);
    return m;
  }

  /** Markers of {@code resource} (and its members for depth one or infinite) of the type and its subtypes. */
  public static IMarker[] find(IResource resource, String type, boolean includeSubtypes, int depth) {
    ensureLoaded();
    List<IMarker> r = new ArrayList<>();
    IPath base = resource.getFullPath();
    synchronized (MARKERS) {
      for (Map.Entry<IPath, List<Marker>> e : MARKERS.entrySet()) {
        IPath p = e.getKey();
        boolean in = p.equals(base) || depth == IResource.DEPTH_INFINITE && base.isPrefixOf(p)
            || depth == IResource.DEPTH_ONE && base.isPrefixOf(p) && p.segmentCount() == base.segmentCount() + 1;
        if (!in)
          continue;
        for (Marker m : e.getValue())
          if (type == null || m.type.equals(type) || includeSubtypes && isSubtype(m.type, type))
            r.add(m);
      }
    }
    return r.toArray(new IMarker[0]);
  }

  public static void delete(IResource resource, String type, boolean includeSubtypes, int depth) {
    ensureLoaded();
    IMarker[] ms = find(resource, type, includeSubtypes, depth);
    if (ms.length == 0)
      return;
    synchronized (MARKERS) {
      for (IMarker m : ms) {
        if (isPersistent(((Marker) m).type))
          persistentChanged = true;
        List<Marker> l = MARKERS.get(m.getResource().getFullPath());
        if (l != null) {
          l.remove(m);
          if (l.isEmpty())
            MARKERS.remove(m.getResource().getFullPath());
        }
      }
    }
    changed(resource);
  }

  /**
   * A resource moved (IResource.move, a renamed project): its markers and those of its members move with it, keeping
   * their ids and attributes, as the Eclipse marker manager does.
   */
  public static void moved(IPath from, IPath to) {
    ensureLoaded();
    if (from.equals(to))
      return;
    List<IResource> touched = new ArrayList<>();
    synchronized (MARKERS) {
      Map<IPath, List<Marker>> moved = new LinkedHashMap<>();
      for (java.util.Iterator<Map.Entry<IPath, List<Marker>>> it = MARKERS.entrySet().iterator(); it.hasNext();) {
        Map.Entry<IPath, List<Marker>> e = it.next();
        if (!from.isPrefixOf(e.getKey()))
          continue;
        it.remove();
        IPath np = to.append(e.getKey().removeFirstSegments(from.segmentCount()));
        IResource nr = handle(e.getValue().get(0).resource.getType(), np);
        for (Marker m : e.getValue()) {
          m.resource = nr;
          if (isPersistent(m.type))
            persistentChanged = true;
        }
        moved.put(np, e.getValue());
        touched.add(e.getValue().get(0).resource);
      }
      MARKERS.putAll(moved);
    }
    for (IResource r : touched)
      changed(r);
  }

  /**
   * Forgets the markers in memory: the persistent ones are read again from the workspace's file on the next use, as
   * after a restart of the server.
   */
  public static void reload() {
    synchronized (MARKERS) {
      MARKERS.clear();
      file = null;
      loaded = false;
    }
  }

  /** The workspace state file of the server (the markers are kept next to it), or null. */
  private static File workspaceStateFile;

  /**
   * The workspace metadata of the markers is next to the workspace state ({@code <name>-markers.json}), as Eclipse keeps
   * them in the workspace metadata; the markers in memory are read again from it.
   */
  public static void setWorkspaceStateFile(File stateFile) {
    synchronized (MARKERS) {
      workspaceStateFile = stateFile;
      for (List<Marker> l : MARKERS.values())
        l.removeIf(m -> isPersistent(m.type));
      MARKERS.values().removeIf(List::isEmpty);
      file = null;
      loaded = false;
    }
  }

  /** The persistent markers are read from the file next to the workspace state once it is known. */
  private static void ensureLoaded() {
    synchronized (MARKERS) {
      if (loaded)
        return;
      if (workspaceStateFile == null) {
        // no workspace yet: nothing to read (the markers live in memory until it is known)
        return;
      }
      String name = workspaceStateFile.getName();
      if (name.endsWith(".json"))
        name = name.substring(0, name.length() - ".json".length());
      file = new File(workspaceStateFile.getAbsoluteFile().getParentFile(), name + "-markers.json");
      read();
      loaded = true;
    }
  }

  private static String digest(String s) {
    try {
      byte[] h = java.security.MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 8; i++)
        sb.append(String.format("%02x", h[i]));
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      return Integer.toHexString(s.hashCode());
    }
  }

  /** Reads the persistent markers of {@link #file} (guarded by MARKERS). */
  private static void read() {
    if (!file.isFile())
      return;
    try {
      JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
          .getAsJsonObject();
      long maxId = 0;
      for (JsonElement e : o.getAsJsonArray("markers")) {
        Marker m = Marker.fromJson(e.getAsJsonObject());
        MARKERS.computeIfAbsent(m.resource.getFullPath(), k -> new ArrayList<>()).add(m);
        maxId = Math.max(maxId, m.id);
      }
      final long max = maxId;
      IDS.updateAndGet(v -> Math.max(v, max));
    } catch (RuntimeException | IOException e) {
      RLogger.logInfo("Cannot read the markers " + file + ": " + e);
    }
  }

  /** Writes the persistent markers to {@link #file}. */
  private static void save() {
    synchronized (MARKERS) {
      if (file == null)
        return;
      JsonArray markers = new JsonArray();
      for (List<Marker> l : MARKERS.values())
        for (Marker m : l)
          if (isPersistent(m.type))
            markers.add(m.toJson());
      JsonObject o = new JsonObject();
      o.add("markers", markers);
      try {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null)
          dir.mkdirs();
        File tmp = new File(dir, file.getName() + ".tmp");
        Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        FileMoves.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
  }

  private static IResource handle(int type, IPath full) {
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    if (type == IResource.ROOT || full.segmentCount() == 0)
      return root;
    if (type == IResource.PROJECT || full.segmentCount() == 1)
      return root.getProject(full.segment(0));
    return type == IResource.FILE ? root.getFile(full) : root.getFolder(full);
  }

  private static final class Marker implements IMarker {
    final long id;
    volatile IResource resource;
    final String type;
    final long created;
    final Map<String, Object> attrs;

    Marker(IResource resource, String type) {
      this(IDS.incrementAndGet(), resource, type, System.currentTimeMillis(), new HashMap<>());
    }

    Marker(long id, IResource resource, String type, long created, Map<String, Object> attrs) {
      this.id = id;
      this.resource = resource;
      this.type = type;
      this.created = created;
      this.attrs = attrs;
    }

    /** The marker as the persistent file keeps it (attributes of the types string, number and boolean). */
    JsonObject toJson() {
      JsonObject o = new JsonObject();
      o.addProperty("id", id);
      o.addProperty("type", type);
      o.addProperty("resourceType", resource.getType());
      o.addProperty("path", resource.getFullPath().toString());
      o.addProperty("created", created);
      JsonObject a = new JsonObject();
      synchronized (this) {
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
          Object v = e.getValue();
          if (v instanceof Number)
            a.addProperty(e.getKey(), (Number) v);
          else if (v instanceof Boolean)
            a.addProperty(e.getKey(), (Boolean) v);
          else if (v != null)
            a.addProperty(e.getKey(), v.toString());
        }
      }
      o.add("attributes", a);
      return o;
    }

    static Marker fromJson(JsonObject o) {
      IResource r = handle(o.get("resourceType").getAsInt(), new Path(o.get("path").getAsString()));
      Map<String, Object> attrs = new HashMap<>();
      for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("attributes").entrySet()) {
        JsonPrimitive p = e.getValue().getAsJsonPrimitive();
        Object v;
        if (p.isBoolean())
          v = p.getAsBoolean();
        else if (p.isNumber()) {
          long l = p.getAsLong();
          v = l == (int) l ? (Object) (int) l : (Object) l;
        } else
          v = p.getAsString();
        attrs.put(e.getKey(), v);
      }
      return new Marker(o.get("id").getAsLong(), r, o.get("type").getAsString(), o.get("created").getAsLong(), attrs);
    }

    /** A change of the marker: the persistent types are written and the listeners told. */
    private void touched() {
      if (isPersistent(type))
        persistentChanged = true;
      changed(resource);
    }

    @Override
    public void delete() {
      synchronized (MARKERS) {
        List<Marker> l = MARKERS.get(resource.getFullPath());
        if (l != null) {
          l.remove(this);
          if (l.isEmpty())
            MARKERS.remove(resource.getFullPath());
        }
      }
      touched();
    }

    @Override
    public boolean exists() {
      synchronized (MARKERS) {
        List<Marker> l = MARKERS.get(resource.getFullPath());
        return l != null && l.contains(this);
      }
    }

    @Override
    public synchronized Object getAttribute(String name) {
      return attrs.get(name);
    }

    @Override
    public synchronized int getAttribute(String name, int def) {
      Object v = attrs.get(name);
      return v instanceof Integer ? (Integer) v : def;
    }

    @Override
    public synchronized String getAttribute(String name, String def) {
      Object v = attrs.get(name);
      return v instanceof String ? (String) v : def;
    }

    @Override
    public synchronized boolean getAttribute(String name, boolean def) {
      Object v = attrs.get(name);
      return v instanceof Boolean ? (Boolean) v : def;
    }

    @Override
    public synchronized Map<String, Object> getAttributes() {
      return new HashMap<>(attrs);
    }

    @Override
    public synchronized Object[] getAttributes(String[] names) {
      Object[] r = new Object[names.length];
      for (int i = 0; i < names.length; i++)
        r[i] = attrs.get(names[i]);
      return r;
    }

    @Override
    public long getCreationTime() {
      return created;
    }

    @Override
    public long getId() {
      return id;
    }

    @Override
    public IResource getResource() {
      return resource;
    }

    @Override
    public String getType() {
      return type;
    }

    @Override
    public boolean isSubtypeOf(String superType) {
      return isSubtype(type, superType);
    }

    @Override
    public void setAttribute(String name, int value) {
      setAttribute(name, (Object) value);
    }

    @Override
    public void setAttribute(String name, Object value) {
      synchronized (this) {
        if (value == null)
          attrs.remove(name);
        else
          attrs.put(name, value);
      }
      touched();
    }

    @Override
    public void setAttribute(String name, boolean value) {
      setAttribute(name, (Object) value);
    }

    @Override
    public void setAttributes(String[] names, Object[] values) {
      synchronized (this) {
        for (int i = 0; i < names.length; i++)
          if (values[i] == null)
            attrs.remove(names[i]);
          else
            attrs.put(names[i], values[i]);
      }
      touched();
    }

    @Override
    public void setAttributes(Map<String, ? extends Object> map) {
      synchronized (this) {
        attrs.clear();
        if (map != null)
          attrs.putAll(map);
      }
      touched();
    }
  }

  static CoreException noResource(IResource r) {
    return FsResource.error("Resource does not exist: " + r.getFullPath(), null);
  }
}
