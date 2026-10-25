package com.unitesk.requality.vscode.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.MoveOperation;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * apply_changes: a batch of operations on one project, made in the transaction of the caller (one undo step), all of
 * them or none. Later operations refer to the nodes earlier ones created by their {@code as} name ("$name").
 */
final class Changes {
  static final int MAX_OPERATIONS = 500;

  /** The keys each operation takes. */
  static final Map<String, List<String>> KEYS = new LinkedHashMap<>();
  static {
    KEYS.put("create", Arrays.asList("parent", "type", "id", "name", "description", "attributes", "target", "template", "as"));
    KEYS.put("update", Arrays.asList("node", "id", "name", "description", "attributes", "as"));
    KEYS.put("update_all", Arrays.asList("under", "type", "where_attribute", "where_value", "attributes"));
    KEYS.put("rename_attribute", Arrays.asList("under", "attribute", "new_name"));
    KEYS.put("delete", Arrays.asList("node", "recursive"));
    KEYS.put("move", Arrays.asList("node", "parent", "after"));
    KEYS.put("copy", Arrays.asList("node", "parent", "as"));
    KEYS.put("link", Arrays.asList("from", "to", "attribute"));
    KEYS.put("unlink", Arrays.asList("from", "to", "attribute"));
    KEYS.put("locate", Arrays.asList("requirement", "document", "quote", "paragraph", "occurrence", "start", "end", "as"));
    KEYS.put("unlocate", Arrays.asList("location", "requirement"));
    KEYS.put("resolve", Arrays.asList("item", "status"));
  }

  /** The keys each operation needs. */
  static final Map<String, List<String>> REQUIRED = new LinkedHashMap<>();
  static {
    REQUIRED.put("create", Arrays.asList("parent"));
    REQUIRED.put("update", Arrays.asList("node"));
    REQUIRED.put("update_all", Arrays.asList("under", "attributes"));
    REQUIRED.put("rename_attribute", Arrays.asList("under", "attribute", "new_name"));
    REQUIRED.put("delete", Arrays.asList("node"));
    REQUIRED.put("move", Arrays.asList("node", "parent"));
    REQUIRED.put("copy", Arrays.asList("node", "parent"));
    REQUIRED.put("link", Arrays.asList("from", "to"));
    REQUIRED.put("unlink", Arrays.asList("from", "to"));
    REQUIRED.put("locate", Arrays.asList("requirement", "document"));
    REQUIRED.put("unlocate", Arrays.asList("location"));
    REQUIRED.put("resolve", Arrays.asList("item"));
  }

  /** What each key means, for the schema of the operations. */
  static final Map<String, String> MEANING = new LinkedHashMap<>();
  static {
    MEANING.put("parent", "The parent node: a path, a UUID, or \"$name\" of an earlier operation.");
    MEANING.put("node", "The node: a path, a UUID, or \"$name\".");
    MEANING.put("type", "create: Requirement (default), TestPurpose, Comment, TextNode, DocFolder, ReportFolder, VirtualNode "
        + "(with target), InstancesFolder or RestrictedFolder. update_all: only nodes of this type.");
    MEANING.put("target", "VirtualNode: the node it reuses (a Requirement, TestPurpose, Comment or VirtualNode); its "
        + "subtree appears under the virtual node.");
    MEANING.put("template", "A node template of /Templates to copy attributes from, or \"none\" (default: the active "
        + "template of the type, as in Requality).");
    MEANING.put("recursive", "delete: true to delete a node that has nodes under it (they go with it).");
    MEANING.put("under", "The subtree root: the operation changes the nodes under it (and it).");
    MEANING.put("where_attribute", "update_all: only nodes that have this attribute (with where_value: of this value).");
    MEANING.put("where_value", "update_all: the value where_attribute must have (compared as text).");
    MEANING.put("new_name", "The new attribute name.");
    MEANING.put("id", "The id (default for create: the next free id).");
    MEANING.put("name", "The name.");
    MEANING.put("description", "Text (paragraphs separated by empty lines) or HTML (starts with '<').");
    MEANING.put("attributes", "User attributes: name -> value (string, number, boolean, list of strings; null removes).");
    MEANING.put("as", "A name for the node, to refer to it later in the call as \"$name\".");
    MEANING.put("after", "A child of the new parent to put the node after (default: last).");
    MEANING.put("from", "The node the link starts at.");
    MEANING.put("to", "The target: a path, a UUID, \"$name\", or \"<project>::<path>\" in another project.");
    MEANING.put("attribute", "The link attribute (default: the default link attribute of the project).");
    MEANING.put("requirement", "The requirement.");
    MEANING.put("document", "The document (under /Documents).");
    MEANING.put("quote", "Text of the document to mark (white space, typographic quotes, dashes and case do not matter).");
    MEANING.put("paragraph", "Paragraph number: with quote, the paragraph to look in; alone, mark all its text.");
    MEANING.put("occurrence", "With quote: the n-th occurrence (1 = first) when the quote occurs more than once.");
    MEANING.put("start", "Start offset of the range to mark (see read_document with ranges).");
    MEANING.put("end", "End offset of the range to mark.");
    MEANING.put("location", "The location UUID.");
    MEANING.put("item", "A review item (see trace_coverage).");
    MEANING.put("status", "COMPLETED (default) or CHECK.");
  }

  /**
   * The node types create makes, as the New menu of Requality (documents come with import_document, report settings
   * with generate_report).
   */
  static final List<String> TYPES = Arrays.asList(Requirement.TYPE_NAME, TestPurpose.TYPE_NAME, "Comment", "TextNode", "DocFolder",
      "ReportFolder", VirtualNode.TYPE_NAME, "InstancesFolder", "RestrictedFolder");

  /** At most this many nodes are named in the result of an operation on many nodes. */
  private static final int NAMED = 20;

  private final TreeDB db;
  private final Map<String, TreeNode> aliases = new HashMap<>();
  private final Map<Document, DocumentText> texts = new HashMap<>();

  private Changes(TreeDB db) {
    this.db = db;
  }

  /** Checks the form of the operations (before a transaction starts). */
  static void check(JsonArray operations) {
    if (operations == null || operations.isEmpty())
      throw new InvalidParams("Give at least one operation");
    if (operations.size() > MAX_OPERATIONS)
      throw new InvalidParams("At most " + MAX_OPERATIONS + " operations in one call");
    for (int i = 0; i < operations.size(); i++) {
      JsonElement e = operations.get(i);
      if (!e.isJsonObject())
        throw new InvalidParams("Operation " + i + " is not an object");
      JsonObject op = e.getAsJsonObject();
      String kind = Core.optStr(op, "op", null);
      if (kind == null)
        throw new InvalidParams("Operation " + i + " has no 'op' (" + String.join(", ", KEYS.keySet()) + ")");
      List<String> keys = KEYS.get(kind);
      if (keys == null)
        throw new InvalidParams("Operation " + i + ": unknown op '" + kind + "' (" + String.join(", ", KEYS.keySet()) + ")");
      for (String k : op.keySet())
        if (!k.equals("op") && !keys.contains(k))
          throw new InvalidParams("Operation " + i + " (" + kind + "): unknown key '" + k + "'; " + kind + " takes "
              + String.join(", ", keys));
      for (String k : REQUIRED.get(kind))
        if (!op.has(k) || op.get(k).isJsonNull())
          throw new InvalidParams("Operation " + i + " (" + kind + "): missing key '" + k + "' (" + MEANING.get(k) + ")");
    }
  }

  /** Makes the operations in the open transaction; returns the result of each one (for the review). */
  static JsonArray run(TreeDB db, JsonArray operations) throws Exception {
    return run(db, operations, false);
  }

  /**
   * {@link #run(TreeDB, JsonArray)}; {@code all} (a dry run, which is rolled back) goes on after a failed operation and
   * reports every failed one.
   */
  static JsonArray run(TreeDB db, JsonArray operations, boolean all) throws Exception {
    check(operations);
    Changes c = new Changes(db);
    JsonArray results = new JsonArray();
    List<String> errors = new ArrayList<>();
    for (int i = 0; i < operations.size(); i++) {
      JsonObject op = operations.get(i).getAsJsonObject();
      String kind = Core.optStr(op, "op", null);
      String error;
      try {
        JsonObject r = c.one(kind, op);
        r.addProperty("i", i);
        results.add(r);
        continue;
      } catch (InvalidParams | ToolError ex) {
        error = ex.getMessage();
      } catch (Exception ex) {
        error = Core.message(ex);
      }
      String e = "Operation " + i + " (" + kind + "): " + error;
      if (!all)
        throw new ToolError(e + ". Nothing was changed.");
      errors.add(e);
      // a later operation that names the node of this one fails too: it says so
      if (op.has("as") && op.get("as").isJsonPrimitive())
        c.failed.put(op.get("as").getAsString(), i);
    }
    if (!errors.isEmpty())
      throw new ToolError(errors.size() + " of " + operations.size() + " operations fail: " + String.join("; ", errors)
          + ". Nothing was changed.");
    return results;
  }

  /** The names ("as") of the operations that failed in a dry run, with their numbers. */
  private final Map<String, Integer> failed = new HashMap<>();

  private TreeNode ref(JsonObject op, String key) {
    String r = Core.str(op, key);
    if (r.startsWith("$")) {
      TreeNode n = aliases.get(r.substring(1));
      if (n == null && failed.containsKey(r.substring(1)))
        throw new InvalidParams(r + " is the node of operation " + failed.get(r.substring(1)) + ", which failed");
      if (n == null)
        throw new InvalidParams("No earlier operation is named " + r.substring(1));
      return n;
    }
    return Core.node(db, r);
  }

  private JsonObject one(String kind, JsonObject op) throws Exception {
    if (kind == null)
      throw new InvalidParams("Missing 'op'");
    switch (kind) {
      case "create":
        return create(op);
      case "update":
        return update(op);
      case "update_all":
        return updateAll(op);
      case "rename_attribute":
        return renameAttribute(op);
      case "delete":
        return delete(op);
      case "move":
        return move(op);
      case "copy":
        return copy(op);
      case "link":
      case "unlink":
        return link(op, kind.equals("link"));
      case "locate":
        return locate(op);
      case "unlocate":
        return unlocate(op);
      case "resolve":
        return resolve(op);
      default:
        throw new InvalidParams("Unknown op '" + kind + "' (" + String.join(", ", KEYS.keySet()) + ")");
    }
  }

  // ---- operations

  /** True when a node of the type can be a child of the parent (the child rules and the parent rules of Requality). */
  private static boolean placeable(TreeLogic tl, String type, TreeNode parent) {
    return tl.canBeCreatedIn(type, parent) && tl.canBeParentOf(parent.getType(), type);
  }

  private JsonObject create(JsonObject op) throws Exception {
    TreeNode parent = ref(op, "parent");
    String type = Core.optEnum(op, "type", Requirement.TYPE_NAME, TYPES.toArray(new String[0]));
    String id = Core.optStr(op, "id", null);
    TreeLogic tl = (TreeLogic) db.getTreeLogic();
    if (!placeable(tl, type, parent)) {
      List<String> allowed = new ArrayList<>();
      for (String t : TYPES)
        if (placeable(tl, t, parent))
          allowed.add(t);
      throw new ToolError("a " + type + " cannot be a child of " + parent.getQualifiedId() + " (a " + parent.getType()
          + "; it takes: " + (allowed.isEmpty() ? "none of " + TYPES : allowed) + ")");
    }
    TreeNode target = null;
    if (type.equals(VirtualNode.TYPE_NAME)) {
      if (!op.has("target") || op.get("target").isJsonNull())
        throw new InvalidParams("a VirtualNode needs 'target' (" + MEANING.get("target") + ")");
      target = ref(op, "target");
    } else if (op.has("target") && !op.get("target").isJsonNull()) {
      throw new InvalidParams("'target' is for a VirtualNode");
    }
    String template = Core.optStr(op, "template", null);
    if (template != null && !template.equals("none")) {
      TreeNode t = db.getTemplate(template);
      if (t == null)
        throw new ToolError("no template " + template + " in /Templates (templates: " + templateIds(db) + ")");
      if (!t.getType().equals(type))
        throw new ToolError("the template " + template + " is a " + t.getType() + ", not a " + type);
    }
    if (id == null)
      id = RequalityCLI.getNextId(parent, type, RequalityCLI.detectPrefix(parent, type, true));
    try {
      tl.canBeCreatedInExc(type, id, parent);
    } catch (Exception e) {
      throw new ToolError("cannot create a " + type + " '" + id + "' in " + parent.getQualifiedId() + ": " + Core.message(e));
    }
    TreeNode c = template == null ? parent.createChild(id, type)
        : parent.createChild(id, type, template.equals("none") ? TreeDB.EMPTY_TEMPLATE : template);
    if (tl.isAutoRenamable(type))
      c.setIndex(Index.getNextIndex(type, parent, null), false);
    if (type.equals(TestPurpose.TYPE_NAME)) {
      c.attrs().setConstAttribute(TestPurpose.ATTR_AUTHOR, System.getProperty("user.name", ""), Flag.HIDDEN);
      c.attrs().setConstAttribute(TestPurpose.ATTR_STATUS, "in process", Flag.HIDDEN);
    }
    if (target != null) {
      VirtualNode vn = (VirtualNode) c;
      String problem = vn.getChildIterator().checkTarget(vn, target);
      if (problem != null)
        throw new ToolError("the VirtualNode cannot reuse " + target.getQualifiedId() + ": " + problem);
      vn.setTarget(target);
    }
    edit(c, op);
    c.commitAttributes();
    alias(op, c);
    JsonObject r = result("create", c);
    r.add("after", snapshot(c));
    // the same node twice (a call repeated without ids): the agent decides
    List<String> same = new ArrayList<>();
    String name = c.getName();
    String text = Core.text(c.getDescription() == null ? "" : c.getDescription()).trim();
    for (TreeNode x : parent.getWorkChildren())
      if (x != null && x != c && !x.isDisposed() && x.getType().equals(type)
          && (name != null && !name.isEmpty() && name.equals(x.getName())
              || !text.isEmpty() && text.equals(Core.text(x.getDescription() == null ? "" : x.getDescription()).trim())))
        same.add(x.getQualifiedId());
    if (!same.isEmpty())
      r.addProperty("warning", "the same name or description as " + same + " (a repeated call?)");
    return r;
  }

  private static List<String> templateIds(TreeDB db) {
    List<String> out = new ArrayList<>();
    for (TreeNode t : db.getTemplates())
      out.add(t.getId() + " (" + t.getType() + ")");
    return out;
  }

  /** update_all: the attributes of the nodes under a node (of a type, with an attribute value). */
  private JsonObject updateAll(JsonObject op) throws Exception {
    TreeNode under = ref(op, "under");
    String type = Core.optStr(op, "type", null);
    String whereAttr = Core.optStr(op, "where_attribute", null);
    String whereValue = Core.optStr(op, "where_value", null);
    if (whereValue != null && whereAttr == null)
      throw new InvalidParams("where_value needs where_attribute");
    if (!op.get("attributes").isJsonObject() || op.getAsJsonObject("attributes").size() == 0)
      throw new InvalidParams("attributes must be an object with at least one attribute");
    JsonObject attrs = new JsonObject();
    attrs.add("attributes", op.get("attributes"));
    JsonArray names = new JsonArray();
    int n = 0;
    for (TreeNode x : subtree(under)) {
      if (x.isVirtual() || type != null && !x.getType().equals(type))
        continue;
      if (whereAttr != null) {
        Attribute a = x.attrs().getAttribute(whereAttr);
        if (a == null || whereValue != null && !whereValue.equals(String.valueOf(a.getValue())))
          continue;
      }
      edit(x, attrs);
      x.commitAttributes();
      if (names.size() < NAMED)
        names.add(x.getQualifiedId());
      n++;
    }
    JsonObject r = result("update_all", under);
    r.addProperty("nodes", n);
    r.add("first", names);
    if (n == 0)
      r.addProperty("note", "No node matched: nothing was changed by this operation");
    return r;
  }

  /** rename_attribute: an attribute of the nodes under a node gets another name (its value and type stay). */
  private JsonObject renameAttribute(JsonObject op) throws Exception {
    TreeNode under = ref(op, "under");
    String from = Core.str(op, "attribute");
    String to = Core.str(op, "new_name");
    for (String k : new String[] { from, to })
      if (k.startsWith("_") || k.startsWith("%"))
        throw new InvalidParams("attribute " + k + ": system attributes keep their names");
    if (!Attribute.checkNameIsValid(to, false))
      throw new InvalidParams("invalid attribute name " + to);
    if (n(from).equals(n(to)))
      throw new InvalidParams("new_name is the same name");
    JsonArray names = new JsonArray();
    int n = 0;
    for (TreeNode x : subtree(under)) {
      if (x.isVirtual())
        continue;
      Attribute a = x.attrs().getAttribute(from);
      // the attribute of the node itself, not one it inherits (its owner is renamed when it is under the node)
      if (a == null || !a.isLocal(x.getUUId()))
        continue;
      if (x.attrs().getAttribute(to) != null && x.attrs().getAttribute(to).isLocal(x.getUUId()))
        throw new ToolError(x.getQualifiedId() + " has both " + from + " and " + to);
      AttributeDefinition d = a.getDefinition();
      if (RequalityTools.COVERAGE_ATTRIBUTES.contains(to) && x instanceof com.unitesk.requality.nodetype.ReportSettings)
        throw new ToolError("attribute " + to + " of report settings: give the coverage file to generate_report");
      x.attrs().setAttribute(new AttributeDefinition(x.getUUId(), to, d.getAvailability(), d.getValueProvider(),
          AttributeDefinition.detectFlags(to)));
      x.attrs().removeAttribute(from);
      x.commitAttributes();
      if (names.size() < NAMED)
        names.add(x.getQualifiedId());
      n++;
    }
    JsonObject r = result("rename_attribute", under);
    r.addProperty("attribute", from);
    r.addProperty("new_name", to);
    r.addProperty("nodes", n);
    r.add("first", names);
    if (n == 0)
      r.addProperty("note", "No node under " + under.getQualifiedId() + " has the attribute " + from + " of its own");
    return r;
  }

  private static String n(String s) {
    return s.trim();
  }

  private JsonObject update(JsonObject op) throws Exception {
    TreeNode n = ref(op, "node");
    JsonObject before = snapshot(n);
    String newId = Core.optStr(op, "id", null);
    if (newId != null && !newId.equals(n.getId())) {
      try {
        db.getTreeLogic().isIdValidExc(n, newId.trim());
      } catch (Exception e) {
        throw new ToolError("cannot change the id to '" + newId + "': " + Core.message(e));
      }
      n.setId(newId.trim());
    }
    edit(n, op);
    n.commitAttributes();
    alias(op, n);
    JsonObject r = result("update", n);
    r.add("before", before);
    r.add("after", snapshot(n));
    return r;
  }

  private JsonObject delete(JsonObject op) throws Exception {
    TreeNode n = ref(op, "node");
    if (!db.getTreeLogic().canBeDeleted(n))
      throw new ToolError(n.getQualifiedId() + " cannot be deleted");
    int below = count(n) - 1;
    if (below > 0 && !Core.optBool(op, "recursive", false))
      throw new ToolError(n.getQualifiedId() + " has " + below + " node" + (below == 1 ? "" : "s") + " under it ("
          + types(n) + "): give recursive true to delete them with it");
    JsonObject r = result("delete", n);
    // what goes with it, also in the short result of apply_changes
    if (below > 0) {
      r.addProperty("nodes_under", below);
      r.addProperty("types_under", types(n));
    }
    JsonObject before = snapshot(n);
    before.addProperty("nodes_under", below);
    r.add("before", before);
    // the links from other nodes to the deleted ones: they will not resolve any more
    JsonArray incoming = new JsonArray();
    String under = n.getQualifiedId() + "/";
    for (TreeNode x : subtree(n))
      for (Map.Entry<String, Map<TreeNode, String>> e : linksTo(x).entrySet())
        for (TreeNode from : e.getValue().keySet())
          if (from != null && !from.isDisposed() && !from.isVirtual() && from != n && !from.getQualifiedId().startsWith(under)) {
            JsonObject l = new JsonObject();
            l.addProperty("from", from.getQualifiedId());
            l.addProperty("attribute", e.getKey());
            l.addProperty("to", x.getQualifiedId());
            incoming.add(l);
          }
    if (incoming.size() > 0) {
      r.add("broken_links", incoming);
      r.addProperty("note", "These links from other nodes do not resolve after the delete");
    }
    n.delete();
    return r;
  }

  /** copy: a node with the nodes under it, as new nodes under a parent (as the Copy and Paste of Requality). */
  private JsonObject copy(JsonObject op) throws Exception {
    TreeNode n = ref(op, "node");
    TreeNode parent = ref(op, "parent");
    if (n.isVirtual())
      throw new ToolError(n.getQualifiedId() + " is a virtual node (a copy of its target): copy the target");
    if (!db.getTreeLogic().isCopyable(n))
      throw new ToolError(n.getQualifiedId() + " cannot be copied");
    if ((parent.getQualifiedId() + "/").startsWith(n.getQualifiedId() + "/"))
      throw new ToolError("a node cannot be copied into itself");
    // the clipboard of the core, in this transaction, without its dialogs
    com.unitesk.requality.core.operation.CopyPasteHelper clipboard = com.unitesk.requality.core.operation.CopyPasteHelper.getInstance();
    Object r;
    try {
      new com.unitesk.requality.core.operation.CopyHandler().copyNodes(java.util.List.of(n), false);
      r = new com.unitesk.requality.core.operation.PasteIntoHandler().pasteNodes(parent, null, true, true, true, true, true);
    } finally {
      clipboard.setEmpty();
    }
    if (!(r instanceof TreeNode[]) || ((TreeNode[]) r).length != 1)
      throw new ToolError(n.getQualifiedId() + " cannot be copied into " + parent.getQualifiedId()
          + (r instanceof String ? ": " + r : ""));
    TreeNode c = ((TreeNode[]) r)[0];
    alias(op, c);
    JsonObject res = result("copy", c);
    res.addProperty("from", n.getQualifiedId());
    res.addProperty("nodes", count(c));
    res.add("after", snapshot(c));
    return res;
  }

  private JsonObject move(JsonObject op) throws Exception {
    TreeNode n = ref(op, "node");
    TreeNode parent = ref(op, "parent");
    TreeNode after = op.has("after") && !op.get("after").isJsonNull() ? ref(op, "after") : null;
    TreeLogic tl = (TreeLogic) db.getTreeLogic();
    if (parent == n || (parent.getQualifiedId() + "/").startsWith(n.getQualifiedId() + "/"))
      throw new ToolError("cannot move " + n.getQualifiedId() + " into its own subtree");
    try {
      if (n.getWorkParent() != parent)
        tl.canBeCopiedToExc(n, parent, false);
    } catch (Exception e) {
      throw new ToolError("cannot move " + n.getQualifiedId() + " to " + parent.getQualifiedId() + ": " + Core.message(e));
    }
    if (tl.checkVNodeRecursion(n, parent))
      throw new ToolError("cannot move " + n.getQualifiedId() + ": a virtual node under it would contain itself");
    String from = n.getQualifiedId();
    new MoveOperation(n, parent, after, true).excecute();
    JsonObject r = result("move", n);
    r.addProperty("from", from);
    return r;
  }

  private JsonObject link(JsonObject op, boolean add) throws Exception {
    TreeNode from = ref(op, "from");
    String toRef = Core.str(op, "to");
    TreeNode to;
    String raw;
    int sep = toRef.indexOf("::");
    if (sep > 0 && !toRef.startsWith("$")) {
      TreeDB other = null;
      for (TreeDB t : com.unitesk.requality.core.storage.ResManager.getManager().getTreeDBs())
        if (t != null && !t.isDisposed() && (toRef.substring(0, sep).equals(t.getSemiProjectName())
            || toRef.substring(0, sep).equals(t.getLocalName())))
          other = t;
      if (other == null)
        throw new ToolError("no loaded project " + toRef.substring(0, sep));
      to = Core.node(other, toRef.substring(sep + 2));
      raw = other.getLocalName() + "::" + to.getUserVisibleName(true);
    } else {
      to = ref(op, "to");
      // the UUID, as the "Fix References" operation of Requality writes it: a rename or a move keeps the link
      raw = to.getSUUId();
    }
    String attr = Core.optStr(op, "attribute", db.getDefReferenceName());
    if (attr.startsWith("_"))
      throw new InvalidParams("system attribute " + attr + " cannot be a link");
    Attribute cur = from.attrs().getAttribute(attr);
    List<Object> raws = new LinkedList<>();
    if (cur != null) {
      if (!(cur.getDefinition().getValueProvider() instanceof ReferenceValueProvider))
        throw new ToolError("attribute " + attr + " of " + from.getQualifiedId() + " is not a reference attribute");
      Object v = cur.getValue();
      if (v instanceof ListValue) {
        for (Object o : ((ListValue) v).getValues())
          raws.add(o instanceof ReferenceValue ? ((ReferenceValue) o).getRaw() : String.valueOf(o));
      } else if (v instanceof ReferenceValue) {
        raws.add(((ReferenceValue) v).getRaw());
      }
    }
    final TreeNode target = to;
    boolean changed;
    if (add) {
      changed = raws.stream().noneMatch(o -> pointsTo(from, o.toString(), target));
      if (changed)
        raws.add(raw);
    } else {
      changed = raws.removeIf(o -> pointsTo(from, o.toString(), target));
      if (!changed)
        throw new ToolError(from.getQualifiedId() + " has no link to " + target.getQualifiedId() + " in " + attr);
    }
    if (changed) {
      if (raws.isEmpty())
        from.attrs().removeAttribute(attr);
      else
        from.attrs().setAttribute(new AttributeDefinition(from.getUUId(), attr,
            new ReferenceValueProvider(AttributeType.LIST, new ListValue(AttributeType.REFERENCE, raws)),
            AttributeDefinition.detectFlags(attr)));
      from.commitAttributes();
    }
    JsonObject r = result(add ? "link" : "unlink", from);
    r.addProperty("to", target.getTreeDB() == db ? target.getQualifiedId() : target.getTreeDB().getSemiProjectName() + "::" + target.getQualifiedId());
    r.addProperty("attribute", attr);
    if (!changed)
      r.addProperty("unchanged", true);
    return r;
  }

  /** Sets a link attribute of a node to one node (its UUID), in the open transaction. */
  static void setLink(TreeNode from, String attr, TreeNode to) {
    List<Object> raws = new LinkedList<>();
    raws.add(to.getSUUId());
    from.attrs().setAttribute(new AttributeDefinition(from.getUUId(), attr,
        new ReferenceValueProvider(AttributeType.LIST, new ListValue(AttributeType.REFERENCE, raws)),
        AttributeDefinition.detectFlags(attr)));
    from.commitAttributes();
  }

  /** The node a link attribute of a node names first, or null. */
  static TreeNode linked(TreeNode from, String attr) {
    Attribute a = from.attrs().getAttribute(attr);
    if (a == null)
      return null;
    Object v = a.getValue();
    Object first = v instanceof ListValue && !((ListValue) v).getValues().isEmpty() ? ((ListValue) v).getValues().get(0) : v;
    String raw = first instanceof ReferenceValue ? ((ReferenceValue) first).getRaw() : first == null ? null : first.toString();
    if (raw == null)
      return null;
    try {
      TreeNode t = from.getTreeDB().getNode(raw, Path.QID | Path.UV | Path.UUID);
      return t == null || t.isDisposed() ? null : t;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** True when the raw reference resolves to the target from the node. */
  private static boolean pointsTo(TreeNode from, String raw, TreeNode target) {
    try {
      // a raw reference is a path, a user-visible id or a UUID, with "<project>::" for another project
      TreeNode t = from.getTreeDB().getNode(raw, Path.QID | Path.UV | Path.UUID);
      if (t != null && t.getUUId().equals(target.getUUId()))
        return true;
    } catch (RuntimeException e) {
      // unresolved
    }
    return raw.equals(target.getSUUId());
  }

  private DocumentText text(Document doc) throws Exception {
    DocumentText t = texts.get(doc);
    if (t == null) {
      t = DocumentText.of(doc);
      texts.put(doc, t);
    }
    return t;
  }

  private JsonObject locate(JsonObject op) throws Exception {
    TreeNode reqNode = ref(op, "requirement");
    if (!(reqNode instanceof Requirement))
      throw new ToolError(reqNode.getQualifiedId() + " is not a requirement");
    Requirement req = (Requirement) reqNode;
    Document doc = DocumentText.document(ref(op, "document"));
    DocumentText dt = text(doc);
    int start;
    int end;
    String quote = Core.optStr(op, "quote", null);
    if (quote != null) {
      int[] f = dt.find(quote, Core.optInt(op, "paragraph", -1), Core.optInt(op, "occurrence", 0));
      start = f[0];
      end = f[1];
    } else if (op.has("paragraph") && !op.has("start")) {
      int p = Core.optInt(op, "paragraph", -1);
      if (p < 0 || p >= dt.paragraphs.size())
        throw new ToolError("no paragraph " + p + " (the document has " + dt.paragraphs.size() + ")");
      // the paragraph without its leading and trailing white space
      start = dt.paragraphs.get(p).start;
      end = dt.paragraphs.get(p).end;
      while (start < end && Character.isWhitespace(dt.text.charAt(start)))
        start++;
      while (end > start && Character.isWhitespace(dt.text.charAt(end - 1)))
        end--;
    } else {
      if (!op.has("start") || !op.has("end"))
        throw new InvalidParams("give 'quote', 'paragraph', or 'start' and 'end'");
      start = Core.optInt(op, "start", -1);
      end = Core.optInt(op, "end", -1);
      if (start < 0 || end > dt.text.length() || start >= end)
        throw new ToolError("invalid range " + start + ".." + end + " (the text has " + dt.text.length() + " characters)");
    }
    // the same text located again (a retried call) gives the location the requirement has
    for (Location have : req.getLocations(false))
      if (have != null && !have.isDisposed() && have.getDocument() == doc) {
        List<int[]> f = DocumentText.fragments(have);
        if (f.size() == 1 && f.get(0)[0] == start && f.get(0)[1] == end) {
          alias(op, have);
          JsonObject r = result("locate", req);
          r.add("location", dt.location(have));
          r.addProperty("unchanged", true);
          return r;
        }
      }
    // the text marked for another requirement too (a requirement made twice, or two requirements of one sentence)
    java.util.Set<String> others = new java.util.LinkedHashSet<>();
    for (Location have : dt.locations)
      if (have != null && !have.isDisposed())
        for (int[] f : DocumentText.fragments(have))
          if (f[0] < end && f[1] > start)
            for (String q : DocumentText.requirements(have))
              if (!q.equals(req.getQualifiedId()))
                others.add(q);
    Location l = doc.createLocation(null);
    List<Object> frags = new ArrayList<>(Arrays.asList((Object) (start + Location.FRAGMENT_SEPARATOR + end)));
    l.attrs().setConstAttribute(Location.ATTR_MARKUP, new ListValue(AttributeType.STRING, frags), Flag.HIDDEN);
    l.setDescription("<p>" + escape(dt.text.substring(start, end).replaceAll("[\\s\\u00a0]+", " ").trim()) + "</p>");
    l.commitAttributes();
    req.addLocation(l.getUUId());
    req.commitAttributes();
    dt.locations.add(l);
    alias(op, l);
    JsonObject r = result("locate", req);
    r.add("location", dt.location(l));
    if (!others.isEmpty())
      r.addProperty("warning", "this text is marked for " + others + " too");
    return r;
  }

  private JsonObject unlocate(JsonObject op) throws Exception {
    TreeNode ln = ref(op, "location");
    if (!(ln instanceof Location))
      throw new ToolError(ln.getQualifiedId() + " is not a location");
    Location l = (Location) ln;
    Document doc = l.getDocument();
    String marked = doc != null ? text(doc).marked(l) : null;
    List<Requirement> reqs = new ArrayList<>(Arrays.asList(l.getRequirements(false, true)));
    reqs.removeIf(r -> r == null || r.isDisposed());
    TreeNode only = op.has("requirement") && !op.get("requirement").isJsonNull() ? ref(op, "requirement") : null;
    if (only != null && !reqs.contains(only))
      throw new ToolError(only.getQualifiedId() + " has no location " + l.getSUUId() + " (its requirements: "
          + DocumentText.requirements(l) + ")");
    JsonArray removedFrom = new JsonArray();
    for (Requirement r : reqs)
      if (only == null || r == only) {
        r.removeLocation(l);
        r.commitAttributes();
        removedFrom.add(r.getQualifiedId());
      }
    // the location goes when no requirement marks it any more
    boolean deleted = only == null || reqs.size() == 1;
    if (deleted)
      l.delete();
    JsonObject r = new JsonObject();
    r.addProperty("op", "unlocate");
    r.addProperty("location", l.getSUUId());
    if (doc != null)
      r.addProperty("document", doc.getQualifiedId());
    if (marked != null)
      r.addProperty("text", marked);
    r.add("requirements", removedFrom);
    r.addProperty("location_deleted", deleted);
    return r;
  }

  /** The status of a review item of a document update (ToDoItem), as Check and Complete of the To-Do List view. */
  private JsonObject resolve(JsonObject op) throws Exception {
    TreeNode item = ref(op, "item");
    if (!(item instanceof com.unitesk.requality.nodetype.ToDoItem))
      throw new ToolError(item.getQualifiedId() + " is not a review item (see trace_coverage, review_items)");
    com.unitesk.requality.core.operation.document.Task.Status st = com.unitesk.requality.core.operation.document.Task.Status
        .valueOf(Core.optEnum(op, "status", "COMPLETED", "COMPLETED", "CHECK"));
    String before = item.attrs().getStringAttributeValue(com.unitesk.requality.nodetype.ToDoItem.ATTR_STATUS);
    item.attrs().setConstAttribute(com.unitesk.requality.nodetype.ToDoItem.ATTR_STATUS, st.toString(), Flag.HIDDEN);
    item.commitAttributes();
    JsonObject r = result("resolve", item);
    if (before != null)
      r.addProperty("before", before);
    r.addProperty("status", st.toString());
    return r;
  }

  // ---- helpers

  private void alias(JsonObject op, TreeNode n) {
    String as = Core.optStr(op, "as", null);
    if (as != null)
      aliases.put(as.startsWith("$") ? as.substring(1) : as, n);
  }

  static List<TreeNode> subtree(TreeNode n) {
    List<TreeNode> out = new ArrayList<>();
    out.add(n);
    for (TreeNode c : n.getWorkChildren())
      if (c != null && !c.isDisposed())
        out.addAll(subtree(c));
    return out;
  }

  /** The user links to a node (not the locations of the requirements). */
  private static Map<String, Map<TreeNode, String>> linksTo(TreeNode n) {
    return linksTo(n, false);
  }

  /** The links to a node by attribute; {@code internal} also the ones of the attributes of Requality (locations). */
  static Map<String, Map<TreeNode, String>> linksTo(TreeNode n, boolean internal) {
    Map<String, Map<TreeNode, String>> m = com.unitesk.requality.core.attribute.reference.RelManager.getRevertLinks(n, false, true);
    Map<String, Map<TreeNode, String>> out = new LinkedHashMap<>();
    if (m != null)
      for (Map.Entry<String, Map<TreeNode, String>> e : m.entrySet())
        if ((internal || !e.getKey().startsWith("_")) && e.getValue() != null)
          out.put(e.getKey(), e.getValue());
    return out;
  }

  /** The numbers of the nodes under a node by type, for example "12 Requirement, 3 Location". */
  private static String types(TreeNode n) {
    Map<String, Integer> m = new java.util.TreeMap<>();
    for (TreeNode x : subtree(n))
      if (x != n)
        m.merge(x.getType(), 1, Integer::sum);
    List<String> out = new ArrayList<>();
    m.forEach((k, v) -> out.add(v + " " + k));
    return String.join(", ", out);
  }

  private static int count(TreeNode n) {
    int c = 1;
    for (TreeNode ch : n.getWorkChildren())
      if (ch != null)
        c += count(ch);
    return c;
  }

  private static JsonObject result(String op, TreeNode n) {
    JsonObject r = new JsonObject();
    r.addProperty("op", op);
    r.addProperty("path", n.getQualifiedId());
    r.addProperty("uuid", n.getSUUId());
    return r;
  }

  /** What review_changes shows of a node: id, name, description (text) and user attributes. */
  static JsonObject snapshot(TreeNode n) {
    JsonObject o = new JsonObject();
    o.addProperty("path", n.getQualifiedId());
    o.addProperty("type", n.getType());
    o.addProperty("id", n.getId());
    if (n.getName() != null)
      o.addProperty("name", n.getName());
    Attribute da = n.attrs().getAttribute(TreeNode.ATTR_DESCRIPTION);
    String d = da == null || da.getValue() == null ? null : Core.text(da.getValue().toString());
    if (d != null && !d.isEmpty())
      o.addProperty("description", d.length() > 1000 ? d.substring(0, 1000) + " …" : d);
    JsonObject attrs = Core.attributes(n, false);
    if (attrs.size() > 0)
      o.add("attributes", attrs);
    return o;
  }

  /** Sets the name, description and attributes of an operation on a node (in the transaction). */
  static void edit(TreeNode n, JsonObject a) {
    if (a.has("name") && !a.get("name").isJsonNull())
      n.setName(a.get("name").getAsString());
    if (a.has("description") && !a.get("description").isJsonNull())
      n.setDescription(html(a.get("description").getAsString()));
    if (a.has("attributes") && a.get("attributes").isJsonObject())
      for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("attributes").entrySet())
        setAttribute(n, e.getKey(), e.getValue());
  }

  /**
   * The enumeration of an attribute of a node: the one of its value now, or the enumeration of the project with the
   * name of the attribute; null when it has none.
   */
  private static String enumOf(TreeNode n, String key) {
    Attribute a = n.attrs().getAttribute(key);
    if (a != null && a.getValue() instanceof com.unitesk.requality.core.attribute.AppliedEnumValue)
      return ((com.unitesk.requality.core.attribute.AppliedEnumValue) a.getValue()).getBaseKey();
    return ProjectSettings.enumDefinition(n.getTreeDB(), key) != null ? key : null;
  }

  /** HTML of a description: HTML as it is, text as paragraphs. */
  static String html(String s) {
    String t = s.trim();
    if (t.isEmpty() || t.startsWith("<"))
      return t;
    StringBuilder b = new StringBuilder();
    for (String p : t.split("\\r?\\n\\s*\\r?\\n"))
      b.append("<p>").append(escape(p.trim()).replaceAll("\\r?\\n", "<br/>")).append("</p>");
    return b.toString();
  }

  static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private static void setAttribute(TreeNode n, String key, JsonElement v) {
    if (key.startsWith("_") || key.startsWith("%"))
      throw new InvalidParams("attribute " + key + ": system attributes are changed with id, name and description");
    if (!Attribute.checkNameIsValid(key, false))
      throw new InvalidParams("invalid attribute name " + key);
    // the source of the test coverage of report settings is read when a report is made: generate_report sets it
    // (coverage_file), only from a file in a folder of the server
    if (n instanceof com.unitesk.requality.nodetype.ReportSettings && RequalityTools.COVERAGE_ATTRIBUTES.contains(key))
      throw new ToolError("attribute " + key + " of report settings: give the coverage file to generate_report "
          + "(coverage_file)");
    if (v == null || v.isJsonNull()) {
      n.attrs().removeAttribute(key);
      return;
    }
    int flag = AttributeDefinition.detectFlags(key);
    // an attribute of an enumeration (its value is one of the enumeration, see get_project_settings)
    String en = enumOf(n, key);
    if (en != null && v.isJsonPrimitive()) {
      com.unitesk.requality.core.attribute.EnumDefinitionValue edv = ProjectSettings.enumDefinition(n.getTreeDB(), en);
      String val = v.getAsString();
      if (edv != null) {
        if (!edv.containsValue(val)) {
          List<String> vs = new ArrayList<>();
          for (com.unitesk.requality.core.attribute.EnumValue x : edv)
            vs.add(String.valueOf(x.getValue()));
          throw new ToolError("attribute " + key + ": " + val + " is not a value of the enumeration " + en + " " + vs);
        }
        n.attrs().setConstAttribute(key, new com.unitesk.requality.core.attribute.AppliedEnumValue(val, null, edv.getType(), en),
            flag);
        return;
      }
    }
    if (v.isJsonArray()) {
      List<Object> items = new ArrayList<>();
      for (JsonElement e : v.getAsJsonArray())
        items.add(e.isJsonNull() ? "" : e.getAsString());
      n.attrs().setConstAttribute(key, new ListValue(AttributeType.STRING, items), flag);
    } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
      n.attrs().setConstAttribute(key, v.getAsBoolean(), flag);
    } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
      double d = v.getAsDouble();
      if (d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE)
        n.attrs().setConstAttribute(key, (int) d, flag);
      else
        n.attrs().setConstAttribute(key, d, flag);
    } else if (v.isJsonPrimitive()) {
      String s = v.getAsString();
      if (Attribute.getHTMLNames().contains(key))
        n.attrs().setConstAttribute(AttributeType.HTML, key, html(s), flag);
      else
        n.attrs().setConstAttribute(key, s, flag);
    } else {
      throw new InvalidParams("attribute " + key + ": unsupported value " + v);
    }
  }
}
