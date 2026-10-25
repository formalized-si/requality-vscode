package com.unitesk.requality.vscode.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * apply_changes: a batch of operations on one project in one transaction of its tree. Either all operations are made
 * (one undo step) or none; {@code dry_run} makes them and rolls them back. Later operations refer to the nodes earlier
 * ones created by their {@code as} name ("$name").
 */
final class Changes {
  static final int MAX_OPERATIONS = 500;

  private final TreeDB db;
  private final Map<String, TreeNode> aliases = new HashMap<>();
  private final Map<Document, DocumentText> texts = new HashMap<>();

  private Changes(TreeDB db) {
    this.db = db;
  }

  /** Makes the operations; returns the result of each one (with before/after for the journal). */
  static JsonArray apply(TreeDB db, JsonArray operations, String label, boolean dryRun) throws Exception {
    if (operations == null || operations.isEmpty())
      throw new InvalidParams("Give at least one operation");
    if (operations.size() > MAX_OPERATIONS)
      throw new InvalidParams("At most " + MAX_OPERATIONS + " operations in one call");
    Changes c = new Changes(db);
    return Core.transaction(db, label, true, !dryRun, () -> {
      JsonArray results = new JsonArray();
      for (int i = 0; i < operations.size(); i++) {
        JsonElement e = operations.get(i);
        if (!e.isJsonObject())
          throw new InvalidParams("Operation " + i + " is not an object");
        JsonObject op = e.getAsJsonObject();
        String kind = Core.optStr(op, "op", null);
        try {
          JsonObject r = c.one(kind, op);
          r.addProperty("i", i);
          results.add(r);
        } catch (InvalidParams | ToolError ex) {
          throw new ToolError("Operation " + i + " (" + kind + "): " + ex.getMessage() + ". Nothing was changed.");
        } catch (Exception ex) {
          throw new ToolError("Operation " + i + " (" + kind + "): " + Core.message(ex) + ". Nothing was changed.");
        }
      }
      return results;
    });
  }

  private TreeNode ref(JsonObject op, String key) {
    String r = Core.str(op, key);
    if (r.startsWith("$")) {
      TreeNode n = aliases.get(r.substring(1));
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
      case "delete":
        return delete(op);
      case "move":
        return move(op);
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
        throw new InvalidParams("Unknown op '" + kind + "' (create, update, delete, move, link, unlink, locate, unlocate, resolve)");
    }
  }

  // ---- operations

  private JsonObject create(JsonObject op) throws Exception {
    TreeNode parent = ref(op, "parent");
    String type = Core.optStr(op, "type", Requirement.TYPE_NAME);
    String id = Core.optStr(op, "id", null);
    TreeLogic tl = (TreeLogic) db.getTreeLogic();
    if (id == null)
      id = RequalityCLI.getNextId(parent, type, RequalityCLI.detectPrefix(parent, type, true));
    try {
      tl.canBeCreatedInExc(type, id, parent);
    } catch (Exception e) {
      throw new ToolError("cannot create a " + type + " '" + id + "' in " + parent.getQualifiedId() + ": " + Core.message(e));
    }
    TreeNode c = parent.createChild(id, type);
    if (tl.isAutoRenamable(type))
      c.setIndex(Index.getNextIndex(type, parent, null), false);
    if (type.equals(TestPurpose.TYPE_NAME)) {
      c.attrs().setConstAttribute(TestPurpose.ATTR_AUTHOR, System.getProperty("user.name", ""), Flag.HIDDEN);
      c.attrs().setConstAttribute(TestPurpose.ATTR_STATUS, "in process", Flag.HIDDEN);
    }
    edit(c, op);
    c.commitAttributes();
    alias(op, c);
    JsonObject r = result("create", c);
    r.add("after", snapshot(c));
    return r;
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
    JsonObject r = result("delete", n);
    JsonObject before = snapshot(n);
    before.addProperty("nodes_under", count(n) - 1);
    r.add("before", before);
    n.delete();
    return r;
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
      raw = to.getUserVisibleName(true);
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
      int p = op.get("paragraph").getAsInt();
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
      start = op.get("start").getAsInt();
      end = op.get("end").getAsInt();
      if (start < 0 || end > dt.text.length() || start >= end)
        throw new ToolError("invalid range " + start + ".." + end + " (the text has " + dt.text.length() + " characters)");
    }
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
    reqs.removeIf(r -> r == null);
    TreeNode only = op.has("requirement") && !op.get("requirement").isJsonNull() ? ref(op, "requirement") : null;
    JsonArray removedFrom = new JsonArray();
    for (Requirement r : reqs)
      if (only == null || r == only) {
        r.removeLocation(l);
        r.commitAttributes();
        removedFrom.add(r.getQualifiedId());
      }
    boolean deleted = false;
    if (only == null || reqs.size() <= 1) {
      l.delete();
      deleted = true;
    }
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
    String status = Core.optStr(op, "status", "COMPLETED").toUpperCase(java.util.Locale.ROOT);
    com.unitesk.requality.core.operation.document.Task.Status st;
    try {
      st = com.unitesk.requality.core.operation.document.Task.Status.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new InvalidParams("status: COMPLETED or CHECK");
    }
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

  private static JsonObject result(String op, TreeNode n) {
    JsonObject r = new JsonObject();
    r.addProperty("op", op);
    r.addProperty("path", n.getQualifiedId());
    r.addProperty("uuid", n.getSUUId());
    return r;
  }

  private static int count(TreeNode n) {
    int c = 1;
    for (TreeNode ch : n.getWorkChildren())
      if (ch != null)
        c += count(ch);
    return c;
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
    if (v == null || v.isJsonNull()) {
      n.attrs().removeAttribute(key);
      return;
    }
    int flag = AttributeDefinition.detectFlags(key);
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
