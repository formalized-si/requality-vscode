package com.unitesk.requality.vscode.mcp;

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
  /** How long a closed project may stay closed before a tool reports it closed (a conversion reopens it). */
  static final long CLOSED_WAIT_MS = 15_000;

  private final HeadlessClient headless;

  Core(HeadlessClient headless) {
    this.headless = headless;
  }

  Workspace workspace() {
    return headless.server().workspace();
  }

  // ---- arguments

  static String str(JsonObject a, String key) {
    JsonElement e = a.get(key);
    if (e == null || e.isJsonNull() || e.getAsString().isEmpty())
      throw new InvalidParams("Missing argument " + key);
    return e.getAsString();
  }

  static String optStr(JsonObject a, String key, String def) {
    JsonElement e = a.get(key);
    return e == null || e.isJsonNull() ? def : e.getAsString();
  }

  static int optInt(JsonObject a, String key, int def) {
    JsonElement e = a.get(key);
    return e == null || e.isJsonNull() ? def : e.getAsInt();
  }

  static boolean optBool(JsonObject a, String key, boolean def) {
    JsonElement e = a.get(key);
    return e == null || e.isJsonNull() ? def : e.getAsBoolean();
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

  /** The tree of a project, after its loading ended (waits for it). */
  TreeDB db(String projectName) {
    Workspace.Project p = project(projectName);
    long end = System.currentTimeMillis() + LOAD_WAIT_MS;
    long closedSince = 0;
    while (true) {
      // a format conversion closes the project and opens it again: a closed project is closed only when it stays so
      if (p.closed) {
        long now = System.currentTimeMillis();
        if (closedSince == 0)
          closedSince = now;
        else if (now - closedSince > CLOSED_WAIT_MS)
          throw new ToolError("Project " + projectName + " is closed (open it with open_project)");
      } else {
        closedSince = 0;
      }
      if (p.error != null)
        throw new ToolError("Project " + projectName + " cannot be loaded: " + p.error);
      TreeDB db = Nodes.findDB(p.localName);
      if (!p.closed && db != null && !p.loading && Nodes.isReady(db))
        return db;
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

  // ---- nodes

  /** A node by its UUID or by its path ("/Requirements/A/B", a qualified id or a user-visible id). */
  static TreeNode node(TreeDB db, String ref) {
    TreeNode n = null;
    try {
      n = db.getNode(UUID.fromString(ref));
    } catch (IllegalArgumentException e) {
      String path = ref.isEmpty() || ref.equals("/") ? "/" : ref;
      n = db.getNode(path, Path.QID | Path.UV | Path.UUID);
    }
    if (n == null || n.isDisposed())
      throw new ToolError("No node " + ref + " in project " + db.getSemiProjectName());
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
        db.commit(undoable);
        open = false;
      }
      return r;
    } finally {
      if (open)
        db.rollback();
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
