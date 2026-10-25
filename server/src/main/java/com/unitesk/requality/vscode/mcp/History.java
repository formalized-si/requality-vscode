package com.unitesk.requality.vscode.mcp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.MoveOperation;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.NodeMapper;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;

/**
 * The history of the changes the agent made in one catalog, kept across sessions in
 * {@code <storage>/journals/<key of the catalog folder>/}: {@code journal.jsonl} has one entry for each change (a batch of
 * apply_changes, an import, a document update, a revert) and its state changes; {@code changes/<n>.json} has the states
 * of the nodes the change touched, before and after it (the attributes in the storage format of the catalog, the
 * parent, the id and the resources); {@code blobs/} has the resource contents of the states before.
 *
 * <p>A revert makes the nodes of a change as they were before it, with their UUIDs (links and locations to them stay
 * valid). It is refused when a node of the change is not as the change left it (a later change, by the agent or in
 * VS Code), unless forced.
 */
final class History {
  /** The histories by the canonical path of their catalog folder. */
  private static final Map<String, History> ALL = new HashMap<>();

  private final File dir;
  private final List<JsonObject> entries = new ArrayList<>();
  private int next = 1;
  /** The journal ends with a line the last server did not finish: the next entry starts a new line. */
  private boolean newLine;

  /** The history of the catalog in the folder; in memory only without a storage folder. */
  static History of(File storage, File catalog) {
    String path;
    try {
      path = catalog.getCanonicalPath();
    } catch (IOException e) {
      path = catalog.getAbsolutePath();
    }
    synchronized (ALL) {
      History h = ALL.get(path);
      if (h == null) {
        h = new History(storage == null ? null : new File(new File(storage, "journals"), sha256(path).substring(0, 16)), path);
        ALL.put(path, h);
      }
      return h;
    }
  }

  History(File dir, String catalog) {
    this.dir = dir;
    if (dir != null) {
      new File(dir, "changes").mkdirs();
      new File(dir, "blobs").mkdirs();
      try {
        Files.writeString(new File(dir, "catalog.txt").toPath(), catalog + "\n", StandardCharsets.UTF_8);
      } catch (IOException e) {
        System.err.println("[mcp] cannot write the history of " + catalog + ": " + e);
      }
      load();
    }
  }

  File blobs() {
    return dir == null ? null : new File(dir, "blobs");
  }

  /** The folder of the history (null when it is in memory only). */
  File dir() {
    return dir;
  }

  private void load() {
    File f = new File(dir, "journal.jsonl");
    if (!f.isFile())
      return;
    Map<Integer, JsonObject> byId = new HashMap<>();
    try {
      byte[] all = Files.readAllBytes(f.toPath());
      newLine = all.length > 0 && all[all.length - 1] != '\n';
      for (String line : new String(all, StandardCharsets.UTF_8).split("\n")) {
        JsonObject o;
        try {
          o = JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
          continue; // a line the last server did not finish
        }
        if (o.has("change")) {
          o.addProperty("session", "earlier");
          entries.add(o);
          byId.put(o.get("change").getAsInt(), o);
          next = Math.max(next, o.get("change").getAsInt() + 1);
        } else if (o.has("state_of") && byId.containsKey(o.get("state_of").getAsInt())) {
          byId.get(o.get("state_of").getAsInt()).addProperty("state", o.get("state").getAsString());
        }
      }
    } catch (IOException e) {
      System.err.println("[mcp] cannot read the history " + f + ": " + e);
    }
  }

  /** Adds an entry for a committed change ({@code reverts}: the change it reverts, or 0); its node states go to their own file. */
  synchronized JsonObject add(String project, String tool, String label, String summary, JsonArray operations, Capture c,
      int reverts) {
    JsonObject e = new JsonObject();
    int n = next++;
    e.addProperty("change", n);
    e.addProperty("time", Instant.now().toString());
    e.addProperty("project", project);
    e.addProperty("tool", tool);
    if (summary != null)
      e.addProperty("summary", summary);
    e.addProperty("label", label);
    e.add("operations", operations);
    e.addProperty("nodes", c == null ? 0 : c.after.size());
    if (reverts > 0)
      e.addProperty("reverts", reverts);
    e.addProperty("state", "done");
    if (c != null) {
      JsonObject states = new JsonObject();
      states.add("before", c.before);
      states.add("after", c.after);
      if (dir == null)
        e.add("states", states);
      else
        write(new File(dir, "changes/" + n + ".json"), JsonRpcConnection.GSON.toJson(states), false);
    }
    entries.add(e);
    append(e);
    return e;
  }

  synchronized List<JsonObject> entries() {
    return new ArrayList<>(entries);
  }

  synchronized JsonObject entry(int change) {
    for (JsonObject e : entries)
      if (e.get("change").getAsInt() == change)
        return e;
    return null;
  }

  /** The node states of a change: {"before": {uuid: state}, "after": {uuid: state}} (null: the node does not exist). */
  synchronized JsonObject states(int change) {
    JsonObject e = entry(change);
    if (e == null)
      return null;
    if (e.has("states"))
      return e.getAsJsonObject("states");
    File f = dir == null ? null : new File(dir, "changes/" + change + ".json");
    if (f == null || !f.isFile())
      return null;
    try {
      return JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (IOException | RuntimeException x) {
      throw new ToolError("The saved states of change " + change + " cannot be read: " + x.getMessage());
    }
  }

  /** Sets the state of an entry (done, undone, reverted) and records it. */
  synchronized void setState(int change, String state) {
    JsonObject e = entry(change);
    if (e == null)
      return;
    e.addProperty("state", state);
    JsonObject m = new JsonObject();
    m.addProperty("time", Instant.now().toString());
    m.addProperty("state_of", change);
    m.addProperty("state", state);
    append(m);
  }

  private void append(JsonObject e) {
    if (dir == null)
      return;
    if (e.has("session")) {
      e = e.deepCopy();
      e.remove("session");
    }
    write(new File(dir, "journal.jsonl"), (newLine ? "\n" : "") + JsonRpcConnection.GSON.toJson(e) + "\n", true);
    newLine = false;
  }

  private static void write(File f, String text, boolean append) {
    try (Writer w = new OutputStreamWriter(new FileOutputStream(f, append), StandardCharsets.UTF_8)) {
      w.write(text);
    } catch (IOException ex) {
      throw new ToolError("The history cannot be written to " + f + ": " + ex.getMessage());
    }
  }

  // ---- node states

  /**
   * The states of the nodes a transaction touched, before it (read in the transaction, from the storage under it) and
   * after it (read after the commit). Only the nodes whose states differ are kept.
   */
  static final class Capture {
    final JsonObject before = new JsonObject();
    final JsonObject after = new JsonObject();
    final File blobs;
    /** The committed transaction: the undo step of the change. */
    TransactionStorage transaction;
    private List<UUID> touched;

    Capture(File blobs) {
      this.blobs = blobs;
    }

    /** In the transaction, before its commit. */
    void before(TreeDB db) throws IOException {
      if (!(db.getStorage() instanceof TransactionStorage))
        throw new IllegalStateException("no transaction");
      TransactionStorage ts = (TransactionStorage) db.getStorage();
      touched = new ArrayList<>(ts.getTouchedNodes());
      ITreeStorage base = ts.getStorage();
      for (UUID u : touched) {
        JsonObject s = state(base, u, blobs);
        before.add(u.toString(), s == null ? com.google.gson.JsonNull.INSTANCE : s);
      }
    }

    /** After the commit. */
    void after(TreeDB db, TransactionStorage ts) throws IOException {
      transaction = ts;
      for (UUID u : touched) {
        JsonObject s = state(db.getStorage(), u, null);
        JsonElement b = before.get(u.toString());
        if (same(b.isJsonNull() ? null : b.getAsJsonObject(), s)) {
          before.remove(u.toString());
          continue;
        }
        after.add(u.toString(), s == null ? com.google.gson.JsonNull.INSTANCE : s);
      }
    }
  }

  /** The state of a node in a storage: id, parent, attributes (storage format) and resources; null when it is absent. */
  static JsonObject state(ITreeStorage s, UUID u, File blobs) throws IOException {
    String id;
    Map<String, AttributeDefinition> attrs;
    try {
      // in a transaction the id of a node it deleted is still known: canLoad tells whether the node is there
      if (!u.equals(s.getRootNode()) && !s.canLoad(u))
        return null;
      id = s.getNodeId(u);
      attrs = id == null ? null : s.getAttributes(u);
    } catch (RuntimeException e) {
      return null;
    }
    if (id == null || attrs == null || attrs.isEmpty())
      return null;
    JsonObject o = new JsonObject();
    o.addProperty("id", id);
    UUID p = s.getParent(u);
    if (p != null)
      o.addProperty("parent", p.toString());
    try {
      // the format of the node files of the catalog: virtual attributes and the id (the file name) are left out
      o.add("attributes", JsonParser.parseString(NodeMapper.toJSON(u, attrs.values(), true).getJSONObject("attributes").toString()));
    } catch (RuntimeException | com.unitesk.jsonclone.JSONException e) {
      o.add("attributes", new JsonObject());
    }
    JsonObject res = new JsonObject();
    Set<String> names = s.listResources(u);
    if (names != null)
      for (String name : new TreeSet<>(names)) {
        try (InputStream in = s.getResourceContents(u, name)) {
          if (in == null)
            continue;
          byte[] bytes = in.readAllBytes();
          String h = sha256(bytes);
          if (blobs != null) {
            File f = new File(blobs, h);
            if (!f.isFile()) {
              File tmp = new File(blobs, h + ".tmp");
              Files.write(tmp.toPath(), bytes);
              Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
          }
          res.addProperty(name, h);
        } catch (IOException e) {
          // a state before a change must be complete (the change is not made); a state after it is only compared
          if (blobs != null)
            throw e;
          res.addProperty(name, "unreadable");
        }
      }
    o.add("resources", res);
    return o;
  }

  /** Two states are the same when both are absent, or their id, parent, attributes and resources are equal. */
  static boolean same(JsonObject a, JsonObject b) {
    if (a == null || b == null)
      return a == b;
    return a.get("id").equals(b.get("id")) && String.valueOf(a.get("parent")).equals(String.valueOf(b.get("parent")))
        && normalize(a.get("attributes")).equals(normalize(b.get("attributes"))) && a.get("resources").equals(b.get("resources"));
  }

  /** Line ends and the white space at the ends of texts do not count (a saved and loaded catalog has its own). */
  static JsonElement normalize(JsonElement e) {
    if (e == null || e.isJsonNull())
      return com.google.gson.JsonNull.INSTANCE;
    if (e.isJsonPrimitive())
      return e.getAsJsonPrimitive().isString() ? new JsonPrimitive(e.getAsString().replace("\r\n", "\n").replace('\r', '\n').strip()) : e;
    if (e.isJsonArray()) {
      JsonArray a = new JsonArray();
      for (JsonElement x : e.getAsJsonArray())
        a.add(normalize(x));
      return a;
    }
    JsonObject o = new JsonObject();
    for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet())
      o.add(m.getKey(), normalize(m.getValue()));
    return o;
  }

  // ---- revert

  /** What a revert would do or did: the nodes it restores, recreates and deletes, and the conflicts. */
  static final class Plan {
    final List<String> conflicts = new ArrayList<>();
    final List<UUID> recreate = new ArrayList<>();
    final List<UUID> restore = new ArrayList<>();
    final List<UUID> delete = new ArrayList<>();
  }

  /**
   * The plan of a revert: each node of the change must be as the change left it (else a conflict); a node the change
   * created must have no nodes under it that the change did not make.
   */
  static Plan plan(TreeDB db, JsonObject states) throws IOException {
    Plan p = new Plan();
    JsonObject before = states.getAsJsonObject("before");
    JsonObject after = states.getAsJsonObject("after");
    for (Map.Entry<String, JsonElement> e : after.entrySet()) {
      UUID u = UUID.fromString(e.getKey());
      JsonObject a = e.getValue().isJsonNull() ? null : e.getValue().getAsJsonObject();
      JsonElement be = before.get(e.getKey());
      JsonObject b = be == null || be.isJsonNull() ? null : be.getAsJsonObject();
      JsonObject now = state(db.getStorage(), u, null);
      if (!same(a, now))
        p.conflicts.add(conflict(db, u, a, now));
      if (b == null) {
        if (now != null) {
          p.delete.add(u);
          TreeNode n = db.getNode(u);
          if (n != null)
            for (TreeNode c : n.getWorkChildren())
              if (c != null && !after.has(c.getSUUId()))
                p.conflicts.add(path(db, u) + " has a node that the change did not make: " + c.getQualifiedId());
        }
      } else if (now == null) {
        p.recreate.add(u);
      } else {
        p.restore.add(u);
      }
    }
    // parents first: a recreated node may be the parent of another one
    p.recreate.sort((x, y) -> depth(before, x) - depth(before, y));
    return p;
  }

  private static int depth(JsonObject before, UUID u) {
    int d = 0;
    JsonElement s = before.get(u.toString());
    while (s != null && !s.isJsonNull() && s.getAsJsonObject().has("parent") && d < 1000) {
      s = before.get(s.getAsJsonObject().get("parent").getAsString());
      d++;
    }
    return d;
  }

  private static String path(TreeDB db, UUID u) {
    TreeNode n = db.getNode(u);
    return n != null && !n.isDisposed() ? n.getQualifiedId() : u.toString();
  }

  private static String conflict(TreeDB db, UUID u, JsonObject after, JsonObject now) {
    if (now == null)
      return "node " + (after != null ? after.get("id").getAsString() + " " : "") + u + " was deleted later";
    if (after == null)
      return path(db, u) + " was made again later";
    String d = difference(after, now);
    int sp = d.indexOf(' ');
    return path(db, u) + " was " + (sp < 0 ? d + " later" : d.substring(0, sp) + " later" + d.substring(sp));
  }

  /** How the state {@code b} differs from {@code a} (both present): moved, renamed, changed (attributes) or resources. */
  static String difference(JsonObject a, JsonObject b) {
    if (!String.valueOf(a.get("parent")).equals(String.valueOf(b.get("parent"))))
      return "moved";
    if (!a.get("id").equals(b.get("id")))
      return "renamed (" + a.get("id").getAsString() + " to " + b.get("id").getAsString() + ")";
    List<String> keys = new ArrayList<>();
    JsonObject x = normalize(a.get("attributes")).getAsJsonObject();
    JsonObject y = normalize(b.get("attributes")).getAsJsonObject();
    Set<String> all = new TreeSet<>(x.keySet());
    all.addAll(y.keySet());
    for (String k : all)
      if (!String.valueOf(x.get(k)).equals(String.valueOf(y.get(k))))
        keys.add(k.equals("_index") ? "position" : k.startsWith("_") ? k.substring(1) : k);
    if (!keys.isEmpty())
      return "changed (attributes " + String.join(", ", keys) + ")";
    return "changed (resources)";
  }

  /** Makes the nodes as they were before the change (in the transaction of the caller). */
  static void apply(TreeDB db, JsonObject states, Plan p, File blobs) throws Exception {
    JsonObject before = states.getAsJsonObject("before");
    for (UUID u : p.recreate)
      recreate(db, u, before.getAsJsonObject(u.toString()), blobs);
    for (UUID u : p.restore)
      restore(db, u, before.getAsJsonObject(u.toString()), blobs);
    for (UUID u : p.delete) {
      TreeNode n = db.getNode(u);
      // a node under a deleted node is gone with it
      if (n != null && !n.isDisposed())
        n.delete();
    }
    // a delete can change other nodes (the locations of a deleted requirement): they are made as before again
    for (int round = 0; round < 2; round++) {
      boolean changed = false;
      for (Map.Entry<String, JsonElement> e : before.entrySet()) {
        if (e.getValue().isJsonNull())
          continue;
        UUID u = UUID.fromString(e.getKey());
        JsonObject b = e.getValue().getAsJsonObject();
        JsonObject now = state(db.getStorage(), u, null);
        if (same(b, now))
          continue;
        changed = true;
        if (now == null)
          recreate(db, u, b, blobs);
        else
          restore(db, u, b, blobs);
      }
      if (!changed)
        break;
    }
    // the check: every node is as before the change
    for (Map.Entry<String, JsonElement> e : states.getAsJsonObject("after").entrySet()) {
      JsonElement be = before.get(e.getKey());
      JsonObject b = be == null || be.isJsonNull() ? null : be.getAsJsonObject();
      JsonObject now = state(db.getStorage(), UUID.fromString(e.getKey()), null);
      if (!same(b, now)) {
        String what = b == null ? "is there" : now == null ? "is missing" : difference(b, now);
        System.err.println("[mcp] revert: " + e.getKey() + "\n  before: " + b + "\n  now:    " + now);
        throw new ToolError("The revert could not make " + (b != null ? b.get("id").getAsString() + " " : "") + e.getKey()
            + " as it was (it " + what + "); nothing was changed");
      }
    }
  }

  /** The attribute definitions of a state, read as the storage reads a node file. */
  private static Map<String, AttributeDefinition> definitions(UUID u, JsonObject state, File blobs) throws IOException {
    // the reader of the core takes the attributes as the first key of a node file
    JsonObject file = new JsonObject();
    file.add("attributes", state.get("attributes"));
    file.addProperty("uuid", u.toString());
    File related = null;
    try {
      // the resources of the node: the related files of HTML attributes (pictures of a description) are among them
      if (state.getAsJsonObject("resources").size() > 0) {
        related = Files.createTempDirectory("requality-revert").toFile();
        for (Map.Entry<String, JsonElement> r : state.getAsJsonObject("resources").entrySet()) {
          File src = blob(blobs, r.getValue().getAsString());
          File dst = new File(related, r.getKey());
          dst.getParentFile().mkdirs();
          Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      Map<String, AttributeDefinition> m;
      try {
        m = NodeMapper.getAttributes(u, new ByteArrayInputStream(file.toString().getBytes(StandardCharsets.UTF_8)),
            TempFolderFilesProvider.getOne(related, true));
      } catch (com.unitesk.jsonclone.JSONException e) {
        throw new ToolError("The saved attributes of " + u + " cannot be read: " + e.getMessage());
      }
      if (m == null)
        throw new ToolError("The saved attributes of " + u + " cannot be read");
      m.put(TreeNode.ATTR_ID, TreeNode.formIdAttribute(u, state.get("id").getAsString()));
      return m;
    } finally {
      if (related != null)
        deleteTree(related);
    }
  }

  private static File blob(File blobs, String hash) {
    File f = blobs == null ? null : new File(blobs, hash);
    if (f == null || !f.isFile())
      throw new ToolError("The saved content " + hash + " is missing from the history");
    return f;
  }

  private static void deleteTree(File f) {
    File[] l = f.listFiles();
    if (l != null)
      for (File c : l)
        deleteTree(c);
    f.delete();
  }

  /** Creates a deleted node again with its UUID, id, attributes and resources (its children are recreated apart). */
  private static void recreate(TreeDB db, UUID u, JsonObject state, File blobs) throws Exception {
    UUID parent = UUID.fromString(state.get("parent").getAsString());
    TreeNode p = db.getNode(parent);
    if (p == null || p.isDisposed())
      throw new ToolError("The parent " + parent + " of " + state.get("id").getAsString() + " is gone");
    if (p.findWorkChild(state.get("id").getAsString()) != null)
      throw new ToolError(p.getQualifiedId() + " has a child " + state.get("id").getAsString() + " again");
    NodeDesc nd = new NodeDesc(u, parent, state.get("id").getAsString(), new HashMap<>(definitions(u, state, blobs)), false);
    for (Map.Entry<String, JsonElement> r : state.getAsJsonObject("resources").entrySet())
      nd.putResource(r.getKey(), blob(blobs, r.getValue().getAsString()));
    db.createChildNode(NodeDescSet.fromNodeDesc(nd, false), TreeDB.EMPTY_TEMPLATE, OFlag.NONE);
    if (db.getNode(u) == null)
      throw new ToolError(state.get("id").getAsString() + " " + u + " could not be made again");
  }

  /** Makes an existing node as it was: its parent, id, attributes and resources. */
  private static void restore(TreeDB db, UUID u, JsonObject state, File blobs) throws Exception {
    TreeNode n = db.getNode(u);
    JsonObject now = state(db.getStorage(), u, null);
    UUID parent = state.has("parent") ? UUID.fromString(state.get("parent").getAsString()) : null;
    if (parent != null && !String.valueOf(now.get("parent")).equals(String.valueOf(state.get("parent")))) {
      TreeNode p = db.getNode(parent);
      if (p == null || p.isDisposed())
        throw new ToolError("The old parent " + parent + " of " + n.getQualifiedId() + " is gone");
      new MoveOperation(n, p, null, true).excecute();
    }
    String id = state.get("id").getAsString();
    if (!id.equals(n.getId()))
      n.setId(id);
    Map<String, AttributeDefinition> defs = definitions(u, state, blobs);
    JsonObject want = normalize(state.get("attributes")).getAsJsonObject();
    JsonObject have = normalize(state(db.getStorage(), u, null).get("attributes")).getAsJsonObject();
    for (String k : have.keySet())
      if (!want.has(k))
        n.attrs().removeAttribute(k);
    for (String k : want.keySet())
      if (!want.get(k).equals(have.get(k)) && defs.get(k) != null)
        n.attrs().setAttribute(defs.get(k), true);
    n.commitAttributes();
    JsonObject wantRes = state.getAsJsonObject("resources");
    JsonObject haveRes = now.getAsJsonObject("resources");
    for (String r : haveRes.keySet())
      if (!wantRes.has(r))
        n.deleteResource(r, OFlag.NONE);
    for (Map.Entry<String, JsonElement> r : wantRes.entrySet())
      if (!r.getValue().equals(haveRes.get(r.getKey())))
        try (InputStream in = Files.newInputStream(blob(blobs, r.getValue().getAsString()).toPath())) {
          n.setResourceContent(r.getKey(), in, OFlag.NONE);
        }
  }

  /**
   * The link index of the core forgets the links to a node when it is deleted, and indexes only the links of a node
   * that is created: after a revert, undo or redo brings nodes back, the nodes whose references resolve to them are
   * indexed again (one pass over the project).
   */
  static void relink(TreeDB db, java.util.Collection<UUID> targets) {
    Set<UUID> back = new java.util.HashSet<>();
    for (UUID u : targets) {
      TreeNode n = db.getNode(u);
      if (n != null && !n.isDisposed())
        back.add(u);
    }
    if (back.isEmpty())
      return;
    java.util.ArrayDeque<TreeNode> todo = new java.util.ArrayDeque<>();
    todo.add(db.getRootNode());
    while (!todo.isEmpty()) {
      TreeNode n = todo.pop();
      for (TreeNode c : n.getWorkChildren())
        if (c != null && !c.isDisposed())
          todo.push(c);
      for (String k : n.attrs().getLocalAttributeKeys()) {
        com.unitesk.requality.core.attribute.Attribute a = n.attrs().getAttribute(k);
        if (a == null || !a.isReference() || a.getValue() == null)
          continue;
        // a reference keeps its resolution, also a failed one (a node of a later session that was absent at the start):
        // a reference to a node that came back, or one that did not resolve, is resolved again
        boolean stale = false;
        for (com.unitesk.requality.core.attribute.ReferenceValue rv : references(a.getValue())) {
          String raw = rv.getRaw();
          for (UUID u : back)
            stale |= raw != null && raw.contains(u.toString());
          stale |= rv.hasErrorsOnResolve(n.attrs());
        }
        if (stale)
          com.unitesk.requality.core.attribute.ReferenceValue.resetAttribute(n.attrs(), a.getType(), a.getValue());
        boolean to = false;
        try {
          Map<TreeNode, String> m = com.unitesk.requality.core.attribute.ReferenceValue.getValuesMap(n.attrs(), a.getType(), a.getValue());
          if (m != null)
            for (TreeNode t : m.keySet())
              to |= t != null && back.contains(t.getUUId());
        } catch (RuntimeException e) {
          // an unresolved reference
        }
        if (to) {
          db.getLinkResolver().initCache(n, true);
          break;
        }
      }
    }
  }

  /** The reference values of an attribute value (one, or the ones of a list). */
  private static List<com.unitesk.requality.core.attribute.ReferenceValue> references(Object v) {
    List<com.unitesk.requality.core.attribute.ReferenceValue> out = new ArrayList<>();
    if (v instanceof com.unitesk.requality.core.attribute.ReferenceValue)
      out.add((com.unitesk.requality.core.attribute.ReferenceValue) v);
    else if (v instanceof com.unitesk.requality.core.attribute.ListValue)
      for (Object o : ((com.unitesk.requality.core.attribute.ListValue) v).getValues())
        out.addAll(references(o));
    return out;
  }

  // ---- helpers

  static String sha256(String s) {
    return sha256(s.getBytes(StandardCharsets.UTF_8));
  }

  static String sha256(byte[] b) {
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest(b);
      StringBuilder sb = new StringBuilder();
      for (byte x : h)
        sb.append(String.format("%02x", x));
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

}
