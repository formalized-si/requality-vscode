package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.checker.INodeMark;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.tree.ITreeLogic;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/** Access to the Requality core for the MCP tools: projects, nodes, transactions and the JSON form of nodes. */
final class Core {
  /** How long a tool waits for a loading project. */
  static final long LOAD_WAIT_MS = 180_000;
  /** How long a write waits while another transaction (an editor, a checker) holds the tree. */
  static final long TRANSACTION_WAIT_MS = 30_000;
  /** How long a converted project may stay closed before a tool reports it closed (a conversion reopens it). */
  static final long CLOSED_WAIT_MS = 15_000;

  private final HeadlessClient headless;

  Core(HeadlessClient headless) {
    this.headless = headless;
  }

  HeadlessClient headless() {
    return headless;
  }

  Workspace workspace() {
    return headless.server().workspace();
  }

  // ---- arguments

  static String str(JsonObject a, String key) {
    String s = optStr(a, key, null);
    if (s == null || s.isEmpty())
      throw new InvalidParams("Missing argument " + key);
    return s;
  }

  /** A string argument; a number or a boolean is taken as its text, an object or an array is an error. */
  static String optStr(JsonObject a, String key, String def) {
    JsonElement e = a.get(key);
    if (e == null || e.isJsonNull())
      return def;
    if (!e.isJsonPrimitive())
      throw new InvalidParams("Argument " + key + " must be a string, not " + e);
    return e.getAsString();
  }

  /** An integer argument (a JSON number or a string of digits). */
  static int optInt(JsonObject a, String key, int def) {
    JsonElement e = a.get(key);
    if (e == null || e.isJsonNull())
      return def;
    try {
      if (e.isJsonPrimitive()) {
        double d = e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : Double.parseDouble(e.getAsString().trim());
        if (d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE)
          return (int) d;
      }
    } catch (NumberFormatException x) {
      // below
    }
    throw new InvalidParams("Argument " + key + " must be an integer, not " + e);
  }

  /** A boolean argument (true, false, or the strings "true" and "false"). */
  static boolean optBool(JsonObject a, String key, boolean def) {
    JsonElement e = a.get(key);
    if (e == null || e.isJsonNull())
      return def;
    if (e.isJsonPrimitive()) {
      if (e.getAsJsonPrimitive().isBoolean())
        return e.getAsBoolean();
      String s = e.getAsString().trim();
      if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))
        return Boolean.parseBoolean(s);
    }
    throw new InvalidParams("Argument " + key + " must be true or false, not " + e);
  }

  /** One of the values (in any case); the value as it is written in {@code values}. */
  static String optEnum(JsonObject a, String key, String def, String... values) {
    String s = optStr(a, key, null);
    if (s == null)
      return def;
    for (String v : values)
      if (v.equalsIgnoreCase(s.trim()))
        return v;
    throw new InvalidParams("Argument " + key + " must be one of " + String.join(", ", values) + ", not " + s);
  }

  // ---- projects

  /** The project with this name (the name of its .project file or its folder name). */
  Workspace.Project project(String name) {
    List<String> names = new ArrayList<>();
    for (Workspace.Project p : workspace().projects()) {
      if (!p.requality)
        continue;
      String pname = CoreUtils.getProjectName(p.dir);
      if (name.equals(p.localName) || name.equals(pname))
        return p;
      names.add(pname != null ? pname : p.localName);
    }
    throw new ToolError("No project " + name + ". Projects: " + names);
  }

  /**
   * True when the catalog of a project has the format of an older Requality: the conversion question was cancelled in
   * this session, or the project is closed (a cancelled conversion closes it) and its format is old.
   */
  boolean needsConversion(Workspace.Project p) {
    if (headless.needsConversion(p.localName))
      return true;
    // a conversion closes the project for a moment: an allowed conversion is no need
    if (headless.conversionAllowed(p.localName) || headless.converted(p.localName))
      return false;
    try {
      return p.closed && !com.unitesk.requality.core.storage.IResTracker.storedVersionCorrect(p.dir.toURI());
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** The time each project was used last (by its local name). */
  private final Map<String, Long> lastUse = new java.util.concurrent.ConcurrentHashMap<>();

  /** A project was used now (its catalog is not idle). */
  void used(TreeDB db) {
    Workspace.Project p = workspace().project(db.getLocalName());
    if (p != null)
      lastUse.put(p.localName, System.currentTimeMillis());
  }

  /**
   * The ready projects to release: the ones no tool used for {@code idleMs} (0: none), and the ones another process
   * waits for that no tool used for {@code wantedMs}.
   */
  List<Workspace.Project> idle(long idleMs, long wantedMs) {
    long now = System.currentTimeMillis();
    List<Workspace.Project> out = new ArrayList<>();
    for (Workspace.Project p : workspace().projects()) {
      if (!p.requality || p.closed || p.released || p.loading || p.error != null)
        continue;
      long unused = now - lastUse.getOrDefault(p.localName, 0L);
      if (!(idleMs > 0 && unused >= idleMs) && !(unused >= wantedMs && workspace().lockWanted(p)))
        continue;
      TreeDB db = Nodes.findDB(p.localName);
      if (db != null && Nodes.isReady(db))
        out.add(p);
    }
    return out;
  }

  /**
   * Releases the catalogs of {@link #idle} (another session or VS Code can open them); a later call loads such a project
   * again. Called when no tool runs.
   */
  void releaseIdle(long idleMs, long wantedMs) {
    for (Workspace.Project p : idle(idleMs, wantedMs)) {
      System.err.println("[mcp] releases the idle catalog " + p.dir);
      workspace().releaseProject(p.dir);
      // the release runs on the loader thread: it ends before a tool can start
      long end = System.currentTimeMillis() + LOAD_WAIT_MS;
      while (!p.released && System.currentTimeMillis() < end)
        sleep(50);
    }
  }

  private final DiskWatch disk = new DiskWatch();

  /**
   * Before a call on a project: when its catalog changed on disk (git pull, another editor), the project is loaded
   * again (when no tool runs); the notice of the result tells it. Null when nothing changed.
   */
  String checkDisk(McpServer mcp, JsonObject args) {
    if (!args.has("project") || !args.get("project").isJsonPrimitive())
      return null;
    Workspace.Project p;
    try {
      p = project(args.get("project").getAsString());
    } catch (ToolError e) {
      return null; // the tool tells it
    }
    // no change runs while the files are compared with the tree
    boolean outdated = mcp.shared(() -> {
      TreeDB db = Nodes.findDB(p.localName);
      return db != null && !p.loading && !p.closed && p.error == null && Nodes.isReady(db) && disk.outdated(p.dir, db);
    });
    if (!outdated)
      return null;
    System.err.println("[mcp] the catalog " + p.dir + " changed on disk: the project loads again");
    mcp.exclusive(() -> {
      workspace().reloadProject(p.dir);
      disk.forget(p.dir);
    });
    return "The catalog changed on disk (git pull or another editor): the server loaded the project again before this "
        + "call. Undo and redo of the earlier changes end here; revert_change still works.";
  }

  /** The projects whose files of an interrupted import were looked for. */
  final java.util.Set<String> recovered = java.util.concurrent.ConcurrentHashMap.newKeySet();

  /**
   * Removes the files an import left when the server stopped during it (see {@link RequalityTools#markImport}): the
   * files of the document folder that were not there before it and belong to no node.
   */
  void recoverImport(Workspace.Project p, TreeDB db) {
    if (!recovered.add(p.localName))
      return;
    File history = History.of(headless.server().options().storage, p.dir).dir();
    File m = history == null ? null : new File(history, RequalityTools.IMPORT_MARKER);
    if (m == null || !m.isFile())
      return;
    try {
      JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(m.toPath())).getAsJsonObject();
      File dir = new File(o.get("dir").getAsString());
      TreeNode folder = db.getNode(UUID.fromString(o.get("folder").getAsString()));
      java.util.Set<String> before = new java.util.HashSet<>();
      o.getAsJsonArray("before").forEach(x -> before.add(x.getAsString()));
      String[] now = dir.list();
      if (folder != null && !folder.isDisposed() && now != null)
        for (String n : now) {
          if (before.contains(n))
            continue;
          String id = n.endsWith(".json") ? n.substring(0, n.length() - 5)
              : n.endsWith("_resources") ? n.substring(0, n.length() - 10) : n;
          if (folder.findWorkChild(id) == null) {
            System.err.println("[mcp] removes " + new File(dir, n) + ", which an interrupted import left");
            RequalityTools.deleteTree(new File(dir, n));
          }
        }
    } catch (IOException | RuntimeException e) {
      System.err.println("[mcp] cannot read the record of an interrupted import " + m + ": " + e);
    }
    m.delete();
  }

  /** The tree of a project, after its loading ended (waits for it). */
  TreeDB db(String projectName) {
    Workspace.Project p = project(projectName);
    lastUse.put(p.localName, System.currentTimeMillis());
    // a catalog released when idle loads again (another process may hold it now: then the project tells it); until the
    // load starts, the project stays released and the loop below waits
    if (p.released)
      workspace().loadProject(p.dir);
    long end = System.currentTimeMillis() + LOAD_WAIT_MS;
    long closedSince = 0;
    boolean asked = false;
    while (true) {
      if (needsConversion(p))
        throw new ToolError("Project " + projectName + " has the catalog format of an older Requality. Convert it with "
            + "convert_project (it makes a backup first), or start requality-mcp with --convert");
      // a format conversion closes the project and opens it again: a converted project is closed when it stays so
      if (p.closed) {
        long now = System.currentTimeMillis();
        if (closedSince == 0)
          closedSince = now;
        if (!headless.converted(p.localName) || now - closedSince > CLOSED_WAIT_MS)
          throw new ToolError("Project " + projectName + " is closed (open it with open_project)");
      } else {
        closedSince = 0;
      }
      String error = p.error;
      if (error != null && error.startsWith(Workspace.LOCKED)) {
        // Another process holds the catalog: the wait asks it to give the catalog up (an idle requality-mcp does). The
        // project loads when the lock comes.
        if (!asked) {
          asked = true;
          workspace().requestLock(p);
          end = Math.max(end, System.currentTimeMillis() + LOAD_WAIT_MS);
        }
        if (workspace().waitsForLock(p)) {
          sleep(200);
          continue;
        }
        throw new ToolError("Project " + projectName + " is open in another Requality process (VS Code or another "
            + "requality-mcp session that is in use): " + p.dir + ". Close it there (an idle requality-mcp session gives it "
            + "up when asked), then call again");
      }
      if (error != null)
        throw new ToolError("Project " + projectName + " cannot be loaded: " + error);
      TreeDB db = Nodes.findDB(p.localName);
      if (!p.closed && db != null && !p.loading && Nodes.isReady(db)) {
        recoverImport(p, db);
        return db;
      }
      if (System.currentTimeMillis() > end)
        throw new ToolError("Project " + projectName + " is still loading");
      sleep(200);
    }
  }

  static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ToolError("Interrupted");
    }
  }

  /**
   * Refuses a change of a catalog this server loaded without its lock (no folder for the lock files): another process
   * could write it at the same time.
   */
  void writable(TreeDB db) {
    Workspace.Project p = workspace().project(db.getLocalName());
    String why = p != null ? p.unlocked : null;
    if (why != null)
      throw new ToolError("The server does not change project " + db.getSemiProjectName() + ": its catalog is not locked ("
          + why + "), so VS Code or another session could write it at the same time. Start requality-mcp with --locks "
          + "<a folder it can write> to lock the catalogs");
  }

  // ---- nodes

  /**
   * A node by its UUID, its path ("/Requirements/A/B"), its user-visible id (with the id prefix of the project), or its
   * id alone when one node of the project has it.
   */
  static TreeNode node(TreeDB db, String ref) {
    TreeNode n = null;
    try {
      n = db.getNode(UUID.fromString(ref));
    } catch (IllegalArgumentException e) {
      String path = ref.isEmpty() || ref.equals("/") ? "/" : ref;
      n = db.getNode(path, Path.QID | Path.UV | Path.UUID);
    }
    if ((n == null || n.isDisposed()) && !ref.isEmpty() && !ref.contains("/")) {
      // an id alone (LIM_04): the node when the id is unique
      com.unitesk.requality.core.operation.mass.NodeSearch s = new com.unitesk.requality.core.operation.mass.NodeSearch(
          new com.unitesk.requality.core.search.AttributeRequest(TreeNode.ATTR_ID,
              com.unitesk.requality.core.search.AttributeOperator.EQUALS, ref, false));
      s.execute(db.getRootNode());
      List<TreeNode> found = new ArrayList<>();
      for (com.unitesk.requality.core.model.Pair<? extends TreeNode, List<String>> m : s.getMatches())
        if (m.getFirst() != null && !m.getFirst().isDisposed() && !m.getFirst().isVirtual())
          found.add(m.getFirst());
      if (found.size() == 1)
        return db.getNode(found.get(0).getUUId());
      if (found.size() > 1) {
        List<String> paths = new ArrayList<>();
        for (TreeNode f : found.subList(0, Math.min(10, found.size())))
          paths.add(f.getQualifiedId());
        throw new ToolError("Several nodes have the id " + ref + ": " + paths + (found.size() > 10 ? " and more" : "")
            + "; give the path");
      }
    }
    if (n == null || n.isDisposed())
      throw new ToolError("No node " + ref + " in project " + db.getSemiProjectName()
          + " (give a path such as /Requirements/A/B, a UUID, or an id that one node has)");
    return n;
  }

  TreeNode node(JsonObject a, String projectKey, String nodeKey) {
    return node(db(str(a, projectKey)), str(a, nodeKey));
  }

  /** Runs the body in a transaction of the tree (retried while another transaction holds it); commits or rolls back. */
  static <T> T write(TreeDB db, String name, Callable<T> body) throws Exception {
    return write(db, name, true, body);
  }

  /** {@link #write(TreeDB, String, Callable)}; {@code undoable} false keeps the change out of the undo history. */
  static <T> T write(TreeDB db, String name, boolean undoable, Callable<T> body) throws Exception {
    return transaction(db, name, undoable, true, body);
  }

  /** A transaction that commits ({@code commit}) or rolls back (a dry run) when the body ends without an exception. */
  static <T> T transaction(TreeDB db, String name, boolean undoable, boolean commit, Callable<T> body) throws Exception {
    return transaction(db, name, undoable, commit, null, body);
  }

  /**
   * {@link #transaction(TreeDB, String, boolean, boolean, Callable)} that records the node states before and after the
   * commit in {@code capture} (null: none), for the history of the agent changes.
   */
  static <T> T transaction(TreeDB db, String name, boolean undoable, boolean commit, History.Capture capture, Callable<T> body)
      throws Exception {
    long end = System.currentTimeMillis() + TRANSACTION_WAIT_MS;
    while (!db.startTransaction(name, true)) {
      if (System.currentTimeMillis() > end)
        throw new ToolError("Project " + db.getSemiProjectName() + " is busy (another change is running); try again");
      sleep(100);
    }
    boolean open = true;
    try {
      T r = body.call();
      if (commit) {
        if (capture != null)
          capture.before(db);
        // the commit ends the transaction also when it fails: no rollback after it
        open = false;
        com.unitesk.requality.core.transaction.TransactionStorage ts;
        try {
          ts = db.commit(undoable);
        } catch (RuntimeException e) {
          e.printStackTrace(System.err);
          throw new ToolError("The change could not be saved: " + message(e) + ". Parts of it can be in the project: check "
              + "it with get_tree and get_node");
        }
        if (capture != null)
          capture.after(db, ts);
      }
      return r;
    } finally {
      if (open)
        rollback(db);
    }
  }

  /**
   * Rolls back the open transaction. The link index of the core follows the changes of a transaction as they are made
   * and does not follow a rollback: the links of the nodes it touched, and the links to them, are indexed again.
   */
  private static void rollback(TreeDB db) {
    java.util.Set<UUID> touched = java.util.Collections.emptySet();
    // The rollback of the core writes the inverse of each operation to the files: the state the server loaded. A file
    // that changed on disk meanwhile (git pull, another editor) is kept as it is.
    Map<java.io.File, byte[]> external = new java.util.HashMap<>();
    List<java.io.File> missing = new ArrayList<>();
    if (db.getStorage() instanceof com.unitesk.requality.core.transaction.TransactionStorage) {
      com.unitesk.requality.core.transaction.TransactionStorage ts = (com.unitesk.requality.core.transaction.TransactionStorage) db
          .getStorage();
      touched = ts.getTouchedNodes();
      com.unitesk.requality.core.storage.ITreeStorage base = ts.getStorage();
      for (UUID u : touched)
        try {
          JsonObject st = History.state(base, u, null);
          if (st == null || History.onDisk(base, u, st))
            continue;
          java.io.File f = base.getJSONResource(u, com.unitesk.requality.core.lock.LockType.NO_LOCK);
          if (f == null)
            continue;
          if (f.isFile())
            external.put(f, java.nio.file.Files.readAllBytes(f.toPath()));
          else
            missing.add(f);
        } catch (Exception e) {
          e.printStackTrace(System.err);
        }
    }
    try {
      db.rollback();
    } catch (RuntimeException e) {
      // the exception of the body tells what failed
      e.printStackTrace(System.err);
      return;
    } finally {
      for (Map.Entry<java.io.File, byte[]> e : external.entrySet())
        try {
          java.nio.file.Files.write(e.getKey().toPath(), e.getValue());
        } catch (java.io.IOException x) {
          x.printStackTrace(System.err);
        }
      for (java.io.File f : missing)
        f.delete();
    }
    try {
      for (UUID u : touched) {
        TreeNode n = db.getNode(u);
        if (n != null && !n.isDisposed())
          db.getLinkResolver().initCache(n, true);
      }
      History.relink(db, touched);
    } catch (RuntimeException e) {
      e.printStackTrace(System.err);
    }
  }

  /** A readable message of a core exception (the tree logic exceptions have no messages). */
  static String message(Exception e) {
    if (e instanceof ITreeLogic.IdContainsWrongChars)
      return "The id contains a character that is not allowed: '" + ((ITreeLogic.IdContainsWrongChars) e).getWrongCharacter() + "'";
    if (e instanceof ITreeLogic.IdEmpty)
      return "The id is empty";
    if (e instanceof ITreeLogic.SameIdExistsInChildren)
      return "A sibling with this id exists";
    if (e instanceof ITreeLogic.PastingIntoLeafException)
      return "This type of node cannot be a child of the parent";
    String m = e.getMessage();
    return e.getClass().getSimpleName() + (m != null ? ": " + m : "");
  }

  // ---- JSON

  /** Plain text of HTML: block elements become lines, paragraphs are separated by empty lines. */
  static String text(String html) {
    if (html == null)
      return null;
    if (html.indexOf('<') < 0)
      return html.trim();
    org.jsoup.nodes.Document d = org.jsoup.Jsoup.parseBodyFragment(html);
    d.outputSettings().prettyPrint(false);
    d.select("br").before("\\n");
    d.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, pre, blockquote, table").after("\\n\\n");
    // the line breaks of the HTML source are spaces; the markers of the block elements are the lines
    String s = d.body().html().replaceAll("\\s+", " ").replace("\\n", "\n");
    s = org.jsoup.parser.Parser.unescapeEntities(org.jsoup.Jsoup.clean(s, "", org.jsoup.safety.Safelist.none(),
        new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)), false);
    StringBuilder out = new StringBuilder();
    for (String line : s.split("\n", -1))
      out.append(line.replaceAll("[ \t\u00a0]+", " ").trim()).append('\n');
    return out.toString().replaceAll("\n{3,}", "\n\n").trim();
  }

  /** Short form of a node: what a listing shows. */
  static JsonObject brief(TreeNode n) {
    JsonObject o = new JsonObject();
    o.addProperty("uuid", n.getSUUId());
    o.addProperty("path", n.getQualifiedId());
    o.addProperty("type", n.getType());
    o.addProperty("id", n.getId());
    String name = n.getName();
    if (name != null)
      o.addProperty("name", name);
    return o;
  }

  /** {@link #brief} with the number of children; the locations of a document are counted apart (read_document shows them). */
  static JsonObject briefWithChildren(TreeNode n) {
    JsonObject o = brief(n);
    if (n instanceof com.unitesk.requality.nodetype.Document) {
      int locations = 0;
      int others = 0;
      for (TreeNode c : n.getWorkChildren())
        if (c instanceof com.unitesk.requality.nodetype.Location)
          locations++;
        else if (c != null)
          others++;
      o.addProperty("children", others);
      o.addProperty("locations", locations);
    } else {
      o.addProperty("children", n.getWorkChildrenUUIDs().size());
    }
    return o;
  }

  /** The value of an attribute as JSON: numbers, booleans, strings, lists; HTML as text; references with their targets. */
  static JsonElement value(TreeNode n, Attribute a, boolean html) {
    Object v = a.getValue();
    if (v == null)
      return JsonNull.INSTANCE;
    AttributeType t = a.getType();
    switch (t) {
      case INT:
      case FLOAT:
        return v instanceof Number ? new JsonPrimitive((Number) v) : new JsonPrimitive(v.toString());
      case BOOL:
        return v instanceof Boolean ? new JsonPrimitive((Boolean) v) : new JsonPrimitive(v.toString());
      case HTML:
        return new JsonPrimitive(html ? v.toString() : text(v.toString()));
      case REFERENCE:
        return reference(n, v);
      case LIST: {
        JsonArray arr = new JsonArray();
        if (v instanceof ListValue)
          for (Object o : ((ListValue) v).getValues())
            arr.add(o instanceof ReferenceValue ? reference(n, o) : new JsonPrimitive(String.valueOf(o)));
        return arr;
      }
      default:
        return new JsonPrimitive(Attribute.valueToString(n.attrs(), t, v));
    }
  }

  private static JsonElement reference(TreeNode n, Object v) {
    if (!(v instanceof ReferenceValue))
      return new JsonPrimitive(String.valueOf(v));
    ReferenceValue rv = (ReferenceValue) v;
    JsonObject o = new JsonObject();
    o.addProperty("ref", rv.getRaw());
    try {
      JsonArray targets = new JsonArray();
      for (TreeNode t : rv.getNodes(n.attrs()))
        if (t != null)
          targets.add(t.getTreeDB() == n.getTreeDB() ? t.getQualifiedId() : t.getTreeDB().getSemiProjectName() + "::" + t.getQualifiedId());
      o.add("targets", targets);
    } catch (RuntimeException e) {
      o.addProperty("error", "unresolved");
    }
    return o;
  }

  /** The user attributes of a node (not the system ones, whose names start with "_"). */
  static JsonObject attributes(TreeNode n, boolean html) {
    JsonObject o = new JsonObject();
    List<String> keys = new ArrayList<>(n.attrs().getLocalAttributeKeys());
    java.util.Collections.sort(keys);
    for (String k : keys) {
      if (k.startsWith("_") || k.startsWith("%"))
        continue;
      Attribute a = n.attrs().getAttribute(k);
      if (a == null || !a.isUserVisible())
        continue;
      JsonObject e = new JsonObject();
      e.addProperty("type", a.getType().name());
      e.add("value", value(n, a, html));
      if (!a.isLocal(n.getUUId()))
        e.addProperty("inherited", true);
      o.add(k, e);
    }
    return o;
  }

  /** Outgoing (links) or incoming (linkedFrom) references by attribute. */
  static JsonObject links(TreeNode n, boolean incoming) {
    JsonObject o = new JsonObject();
    Map<String, Map<TreeNode, String>> m = incoming ? RelManager.getRevertLinks(n, false, true) : RelManager.getLinks(n, false);
    if (m == null)
      return o;
    for (Map.Entry<String, Map<TreeNode, String>> e : m.entrySet()) {
      if (e.getKey().startsWith("_"))
        continue;
      JsonArray arr = new JsonArray();
      for (TreeNode t : e.getValue().keySet())
        if (t != null && !t.isDisposed())
          arr.add(t.getTreeDB() == n.getTreeDB() ? t.getQualifiedId() : t.getTreeDB().getSemiProjectName() + "::" + t.getQualifiedId());
      if (arr.size() > 0)
        o.add(e.getKey(), arr);
    }
    return o;
  }

  /**
   * The links of a node that resolve to no node (a deleted target, a wrong path): attribute and reference. The
   * locations of a requirement are not links.
   */
  static JsonArray brokenLinks(TreeNode n) {
    JsonArray out = new JsonArray();
    for (String k : n.attrs().getLocalAttributeKeys()) {
      if (k.startsWith("_"))
        continue;
      Attribute a = n.attrs().getAttribute(k);
      if (a == null || !a.isReference() || a.getValue() == null)
        continue;
      for (ReferenceValue rv : History.references(a.getValue())) {
        TreeNode[] targets;
        try {
          targets = rv.getNodes(n.attrs());
        } catch (RuntimeException e) {
          targets = null;
        }
        if (targets == null || targets.length == 0) {
          JsonObject o = new JsonObject();
          o.addProperty("attribute", k);
          o.addProperty("ref", rv.getRaw());
          out.add(o);
        }
      }
    }
    return out;
  }

  static JsonObject problem(IMark m, TreeNode where) {
    JsonObject o = new JsonObject();
    TreeNode target = where;
    if (m instanceof INodeMark && ((INodeMark) m).getTarget() instanceof TreeNode)
      target = (TreeNode) ((INodeMark) m).getTarget();
    if (target != null)
      o.addProperty("node", target.getQualifiedId());
    String cls = m.getClass().getSimpleName();
    o.addProperty("severity", m instanceof com.unitesk.requality.core.checker.IWarningMark || cls.contains("Warning") ? "warning" : "error");
    o.addProperty("message", m.getDescription());
    if (m.getCheckerMessageId() != null)
      o.addProperty("checker", m.getCheckerMessageId());
    return o;
  }
}
