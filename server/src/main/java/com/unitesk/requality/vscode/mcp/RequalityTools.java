package com.unitesk.requality.vscode.mcp;

import java.io.File;
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
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.GenerateReportHandler;
import com.unitesk.requality.core.operation.IGenerateReportHandler;
import com.unitesk.requality.core.operation.ImportDocument;
import com.unitesk.requality.core.operation.MoveOperation;
import com.unitesk.requality.core.operation.NullProgressMonitor;
import com.unitesk.requality.core.operation.ReportExportHandler;
import com.unitesk.requality.core.operation.ReportGenResult;
import com.unitesk.requality.core.operation.document.SAXTextExtractor;
import com.unitesk.requality.core.operation.mass.NodeSearch;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.core.search.AttributeOperator;
import com.unitesk.requality.core.search.AttributeRequest;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateParamDefinition;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.mcp.McpServer.Tool;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/** The MCP tools of Requality: they work on the Requality core of the headless server. */
final class RequalityTools {
  private RequalityTools() {
  }

  private static final String PROJECT = "Project name (see list_projects).";
  private static final String NODE = "Node: its UUID or its path in the project, for example /Requirements/A/B.";
  private static final String DESCRIPTION = "Description. Text (paragraphs separated by empty lines) or HTML (starts with '<').";

  static void register(McpServer mcp, HeadlessClient headless) {
    Core core = new Core(headless);

    // ---- read

    mcp.add(new Tool("list_projects", "List projects",
        "Lists the Requality projects (catalogs) in the workspace folders with their state: loading, ready, broken or closed.",
        Schema.object().build(), true, false, a -> {
          JsonObject r = headless.call("workspace/projects", null).getAsJsonObject();
          JsonArray out = new JsonArray();
          for (JsonElement e : r.getAsJsonArray("projects")) {
            JsonObject p = e.getAsJsonObject();
            JsonObject o = new JsonObject();
            o.add("name", p.get("name"));
            o.add("path", p.get("path"));
            o.add("state", p.get("state"));
            if (p.has("error"))
              o.add("error", p.get("error"));
            out.add(o);
          }
          return obj("projects", out);
        }));

    mcp.add(new Tool("get_tree", "Get tree",
        "The subtree of a node: the children (UUID, path, type, id, name, number of children) to the given depth. "
            + "The top folders of a project are /Requirements, /Documents, /Reports, /Types and /Templates.",
        Schema.object().prop("project", "string", PROJECT, true).prop("node", "string", NODE + " Default: the project root.", false)
            .prop("depth", "integer", "Levels of children (1 to 10, default 2).", false)
            .prop("max_nodes", "integer", "At most this many nodes (default 500).", false).build(),
        true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          TreeNode root = Core.node(db, Core.optStr(a, "node", "/"));
          int depth = Math.max(1, Math.min(10, Core.optInt(a, "depth", 2)));
          int[] budget = { Core.optInt(a, "max_nodes", 500) };
          JsonObject o = Core.briefWithChildren(root);
          o.add("nodes", subtree(root, depth, budget));
          if (budget[0] < 0)
            o.addProperty("truncated", true);
          return o;
        }));

    mcp.add(new Tool("get_node", "Get node",
        "All about one node: id, name, type, description, user attributes, outgoing and incoming links, the document "
            + "locations of a requirement (with the marked text), checker problems, parent and children, and the node "
            + "types that can be created in it.",
        Schema.object().prop("project", "string", PROJECT, true).prop("node", "string", NODE, true)
            .prop("html", "boolean", "Give descriptions and HTML attributes as HTML (default: plain text).", false).build(),
        true, false, a -> detail(core.node(a, "project", "node"), Core.optBool(a, "html", false))));

    mcp.add(new Tool("search_nodes", "Search nodes",
        "Finds nodes under a node. With 'text': the nodes whose user-visible attributes (id, name, description and the "
            + "others) contain the text. With 'attribute': the nodes where this attribute matches 'operator' and 'value'. "
            + "Each match gives the attributes that matched.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("text", "string", "Text to find in the user-visible attributes (case-insensitive).", false)
            .prop("attribute", "string", "Attribute name, for example status or %Name.", false)
            .prop("operator", enumSchema("Comparison for 'attribute' (default CONTAINS).", "EQUALS", "NOT_EQUALS", "CONTAINS",
                "NOT_CONTAINS", "EXISTS", "NOT_EXISTS"), false)
            .prop("value", "string", "Value for 'attribute'.", false)
            .prop("under", "string", NODE + " Search only under this node (default: the project root).", false)
            .prop("type", "string", "Only nodes of this type, for example Requirement.", false)
            .prop("limit", "integer", "At most this many matches (default 100).", false).build(),
        true, false, a -> search(core, a)));

    mcp.add(new Tool("get_problems", "Get problems",
        "The checker problems of a node and the nodes under it (default: the whole project). With 'refresh' the "
            + "checkers run again first.",
        Schema.object().prop("project", "string", PROJECT, true).prop("node", "string", NODE + " Default: the project root.", false)
            .prop("refresh", "boolean", "Run the checkers again (default false).", false)
            .prop("limit", "integer", "At most this many problems (default 200).", false).build(),
        true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          TreeNode root = Core.node(db, Core.optStr(a, "node", "/"));
          boolean refresh = Core.optBool(a, "refresh", false);
          int limit = Core.optInt(a, "limit", 200);
          JsonArray out = new JsonArray();
          int checked = 0;
          for (TreeNode n : new DeepFirstTreeWalker(root, true, false, false, false, false)) {
            if (n == null || n.isDisposed())
              continue;
            checked++;
            if (refresh)
              n.updateCheckerProblems();
            IMark[] marks = n.getCheckerProblems();
            if (marks != null)
              for (IMark m : marks)
                if (out.size() < limit)
                  out.add(Core.problem(m, n));
          }
          JsonObject r = obj("problems", out);
          r.addProperty("checked_nodes", checked);
          if (out.size() >= limit)
            r.addProperty("truncated", true);
          return r;
        }));

    mcp.add(new Tool("get_document_text", "Get document text",
        "The text of a document of the project (a node under /Documents) and its requirement locations: the character "
            + "ranges, the marked text and the requirements of each location. Ranges, 'offset' and 'length' count the "
            + "characters of the raw text (the text content of the document body). By default runs of white space are "
            + "shown as one space or line break; 'raw' gives the text exactly. Long documents come in parts.",
        Schema.object().prop("project", "string", PROJECT, true).prop("document", "string", "The document: " + NODE, true)
            .prop("offset", "integer", "First character of the raw text (default 0).", false)
            .prop("length", "integer", "Number of raw characters (default 50000).", false)
            .prop("raw", "boolean", "The text exactly as in the document, white space included (default false).", false)
            .prop("locations", "boolean", "Include the locations in this part of the text (default true).", false).build(),
        true, false, a -> {
          TreeNode n = core.node(a, "project", "document");
          Document doc = document(n);
          String text = documentText(doc);
          boolean raw = Core.optBool(a, "raw", false);
          int off = Math.max(0, Math.min(text.length(), Core.optInt(a, "offset", 0)));
          int len = Math.max(0, Math.min(text.length() - off, Core.optInt(a, "length", 50000)));
          JsonObject r = Core.brief(doc);
          r.addProperty("text_length", text.length());
          r.addProperty("offset", off);
          r.addProperty("end", off + len);
          String part = text.substring(off, off + len);
          r.addProperty("text", raw ? part : compact(part));
          if (Core.optBool(a, "locations", true)) {
            JsonArray locs = new JsonArray();
            // the locations with a range in the part of the text
            for (TreeNode c : doc.getWorkChildren())
              if (c instanceof Location && overlaps((Location) c, off, off + len))
                locs.add(location((Location) c, text, raw));
            r.add("locations", locs);
          }
          return r;
        }));

    mcp.add(new Tool("list_report_templates", "List report templates",
        "The report templates the project can use: id, name, group, description and parameters.",
        Schema.object().prop("project", "string", PROJECT, true).build(), true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          JsonArray out = new JsonArray();
          for (Template t : TemplatesManager.getOne(db).getTemplates().values()) {
            if (t.getNotExists())
              continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", t.getId());
            o.addProperty("name", t.getName());
            o.addProperty("group", t.getGroup());
            o.addProperty("description", Core.text(t.getDescription()));
            JsonArray ps = new JsonArray();
            TemplateParamDefinition[] defs = t.getParamDescriptions();
            if (defs != null)
              for (TemplateParamDefinition d : defs) {
                JsonObject p = new JsonObject();
                p.addProperty("name", d.name);
                p.addProperty("description", d.description);
                if (d.defvalue != null)
                  p.addProperty("default", d.defvalue.toString());
                ps.add(p);
              }
            o.add("parameters", ps);
            out.add(o);
          }
          return obj("templates", out);
        }));

    mcp.add(new Tool("server_messages", "Server messages",
        "The messages and questions the Requality core showed (for example the result of a format conversion) and "
            + "the answers the headless server gave, oldest first.",
        Schema.object().build(), true, false, a -> {
          JsonArray out = new JsonArray();
          headless.messages().forEach(out::add);
          return obj("messages", out);
        }));

    // ---- write

    mcp.add(new Tool("create_node", "Create node",
        "Creates a node under a parent: a Requirement under /Requirements or another requirement, a TestPurpose or a "
            + "Comment under a requirement, a DocFolder under /Documents. Without 'id' the next free id is used. "
            + "get_node of the parent lists the types it can contain.",
        Schema.object().prop("project", "string", PROJECT, true).prop("parent", "string", "The parent: " + NODE, true)
            .prop("type", "string", "Node type (default Requirement).", false).prop("id", "string", "Id (unique among the siblings).", false)
            .prop("name", "string", "Name.", false).prop("description", "string", DESCRIPTION, false)
            .prop("attributes", attrsSchema(), false).build(),
        false, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          TreeNode parent = Core.node(db, Core.str(a, "parent"));
          String type = Core.optStr(a, "type", Requirement.TYPE_NAME);
          String id = Core.optStr(a, "id", null);
          TreeLogic tl = (TreeLogic) db.getTreeLogic();
          if (id == null)
            id = RequalityCLI.getNextId(parent, type, RequalityCLI.detectPrefix(parent, type, true));
          try {
            tl.canBeCreatedInExc(type, id, parent);
          } catch (Exception e) {
            throw new ToolError("Cannot create a " + type + " '" + id + "' in " + parent.getQualifiedId() + ": " + Core.message(e));
          }
          final String fid = id;
          TreeNode created = Core.write(db, "Create " + type, () -> {
            TreeNode c = parent.createChild(fid, type);
            if (tl.isAutoRenamable(type))
              c.setIndex(Index.getNextIndex(type, parent, null), false);
            if (type.equals(TestPurpose.TYPE_NAME)) {
              c.attrs().setConstAttribute(TestPurpose.ATTR_AUTHOR, System.getProperty("user.name", ""), Flag.HIDDEN);
              c.attrs().setConstAttribute(TestPurpose.ATTR_STATUS, "in process", Flag.HIDDEN);
            }
            edit(c, a);
            c.commitAttributes();
            return c;
          });
          return detail(created, false);
        }));

    mcp.add(new Tool("update_node", "Update node",
        "Changes a node: its id, name, description and user attributes. An attribute value null removes the attribute.",
        Schema.object().prop("project", "string", PROJECT, true).prop("node", "string", NODE, true)
            .prop("id", "string", "New id.", false).prop("name", "string", "New name (empty: no name).", false)
            .prop("description", "string", DESCRIPTION + " Empty: no description.", false).prop("attributes", attrsSchema(), false)
            .build(),
        false, false, a -> {
          TreeNode n = core.node(a, "project", "node");
          TreeDB db = n.getTreeDB();
          String newId = Core.optStr(a, "id", null);
          if (newId != null && !newId.equals(n.getId()))
            try {
              db.getTreeLogic().isIdValidExc(n, newId);
            } catch (Exception e) {
              throw new ToolError("Cannot change the id to '" + newId + "': " + Core.message(e));
            }
          Core.write(db, "Update " + n.getTypeLabel(), () -> {
            if (newId != null && !newId.equals(n.getId()))
              n.setId(newId.trim());
            edit(n, a);
            n.commitAttributes();
            return null;
          });
          return detail(n, false);
        }));

    mcp.add(new Tool("delete_node", "Delete node",
        "Deletes a node with all nodes under it. The top folders (/Requirements, /Documents, /Reports) cannot be deleted. "
            + "undo restores it.",
        Schema.object().prop("project", "string", PROJECT, true).prop("node", "string", NODE, true).build(), false, true, a -> {
          TreeNode n = core.node(a, "project", "node");
          TreeDB db = n.getTreeDB();
          if (!db.getTreeLogic().canBeDeleted(n))
            throw new ToolError(n.getQualifiedId() + " cannot be deleted");
          JsonObject r = Core.brief(n);
          Core.write(db, "Delete " + n.getTypeLabel(), () -> {
            n.delete();
            return null;
          });
          r.addProperty("deleted", true);
          return r;
        }));

    mcp.add(new Tool("move_node", "Move node",
        "Moves a node (with the nodes under it) to another parent, or to another place among its siblings.",
        Schema.object().prop("project", "string", PROJECT, true).prop("node", "string", NODE, true)
            .prop("parent", "string", "The new parent: " + NODE, true)
            .prop("after", "string", "Put it after this child of the new parent (default: last).", false).build(),
        false, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          TreeNode n = Core.node(db, Core.str(a, "node"));
          TreeNode parent = Core.node(db, Core.str(a, "parent"));
          String afterRef = Core.optStr(a, "after", null);
          TreeNode after = afterRef == null ? null : Core.node(db, afterRef);
          TreeLogic tl = (TreeLogic) db.getTreeLogic();
          if (parent == n || (parent.getQualifiedId() + "/").startsWith(n.getQualifiedId() + "/"))
            throw new ToolError("Cannot move " + n.getQualifiedId() + " into its own subtree");
          try {
            if (n.getWorkParent() != parent)
              tl.canBeCopiedToExc(n, parent, false);
          } catch (Exception e) {
            throw new ToolError("Cannot move " + n.getQualifiedId() + " to " + parent.getQualifiedId() + ": " + Core.message(e));
          }
          if (tl.checkVNodeRecursion(n, parent))
            throw new ToolError("Cannot move " + n.getQualifiedId() + ": a virtual node under it would contain itself");
          Core.write(db, "Move " + n.getTypeLabel(), () -> {
            new MoveOperation(n, parent, after, true).excecute();
            return null;
          });
          return Core.brief(n);
        }));

    mcp.add(new Tool("add_link", "Add link",
        "Adds a link (a reference) from one node to another in a reference list attribute. The default attribute is "
            + "the default link attribute of the project (usually 'elaborates').",
        linkSchema(), false, false, a -> link(core, a, true)));

    mcp.add(new Tool("remove_link", "Remove link", "Removes a link from one node to another (see add_link).", linkSchema(), false,
        false, a -> link(core, a, false)));

    mcp.add(new Tool("create_location", "Create location",
        "Marks text in a document as a location of a requirement (the markup of the Document Editor). Give the text "
            + "with 'quote' (it must occur in the document text, see get_document_text) or its range with 'start' and "
            + "'end'.",
        Schema.object().prop("project", "string", PROJECT, true).prop("document", "string", "The document: " + NODE, true)
            .prop("requirement", "string", "The requirement: " + NODE, true)
            .prop("quote", "string", "Text of the document to mark (differences of white space do not matter).", false)
            .prop("occurrence", "integer", "Which occurrence of 'quote' (1 = first; needed when the quote occurs more than once).", false)
            .prop("start", "integer", "First character of the range in the document text.", false)
            .prop("end", "integer", "End of the range (exclusive).", false).build(),
        false, false, a -> createLocation(core, a)));

    mcp.add(new Tool("import_document", "Import document",
        "Imports a document file (HTML, XHTML, DOC, DOCX, ODT, PDF) into the project as a Document under a document folder.",
        Schema.object().prop("project", "string", PROJECT, true).prop("file", "string", "Absolute path of the file.", true)
            .prop("folder", "string", "The document folder: " + NODE + " Default: /Documents.", false).build(),
        false, false, a -> importDocument(core, a)));

    mcp.add(new Tool("generate_report", "Generate report",
        "Generates a report from a template (see list_report_templates) for the requirements under a node and writes it "
            + "to a folder.",
        Schema.object().prop("project", "string", PROJECT, true).prop("template", "string", "Template id.", true)
            .prop("output", "string", "Absolute path of the output folder.", true)
            .prop("root", "string", "Requirements under this node (default /Requirements).", false)
            .prop("parameters", paramsSchema(), false).build(),
        false, false, a -> generateReport(core, a)));

    mcp.add(new Tool("undo", "Undo", "Undoes the last change (of this server, in any project).", Schema.object().build(), false, false,
        a -> undoRedo(true)));
    mcp.add(new Tool("redo", "Redo", "Redoes the last undone change.", Schema.object().build(), false, false, a -> undoRedo(false)));

    mcp.add(new Tool("open_project", "Open project", "Opens a closed project (see list_projects) and waits until it is loaded.",
        Schema.object().prop("project", "string", PROJECT, true).build(), false, false, a -> {
          String name = Core.str(a, "project");
          headless.server().workspace().openProject(core.project(name).dir);
          core.db(name);
          return obj("project", new com.google.gson.JsonPrimitive(name));
        }));

    mcp.add(new Tool("create_project", "Create project",
        "Creates a new empty Requality project (catalog) in a folder of the workspace.",
        Schema.object().prop("name", "string", "Project name (also the folder name).", true).build(), false, false, a -> {
          String name = Core.str(a, "name");
          try {
            com.unitesk.requality.core.tree.TreeLogic.isIdValidExc(name);
          } catch (Exception e) {
            throw new ToolError("Invalid project name: " + Core.message(e));
          }
          ResManager.getManager().getTracker().createProject(name, name);
          headless.server().workspace().rescan();
          core.db(name);
          return obj("project", new com.google.gson.JsonPrimitive(name));
        }));
  }

  // ---- helpers

  static JsonObject obj(String key, JsonElement value) {
    JsonObject o = new JsonObject();
    o.add(key, value);
    return o;
  }

  static JsonObject enumSchema(String description, String... values) {
    JsonObject s = new JsonObject();
    s.addProperty("type", "string");
    s.addProperty("description", description);
    JsonArray e = new JsonArray();
    for (String v : values)
      e.add(v);
    s.add("enum", e);
    return s;
  }

  static JsonObject attrsSchema() {
    JsonObject s = new JsonObject();
    s.addProperty("type", "object");
    s.addProperty("description", "User attributes: name -> value (string, number, boolean, list of strings; null removes it). "
        + "Names do not start with '_'.");
    return s;
  }

  static JsonObject paramsSchema() {
    JsonObject s = new JsonObject();
    s.addProperty("type", "object");
    s.addProperty("description", "Template parameters: name -> value.");
    return s;
  }

  static JsonObject linkSchema() {
    return Schema.object().prop("project", "string", PROJECT, true).prop("from", "string", "The node with the link: " + NODE, true)
        .prop("to", "string", "The target: " + NODE + " A node of another project: <project>::<path>.", true)
        .prop("attribute", "string", "The reference attribute (default: the default link attribute of the project).", false).build();
  }

  private static JsonArray subtree(TreeNode n, int depth, int[] budget) {
    JsonArray arr = new JsonArray();
    for (TreeNode c : n.getSortedWorkChildren()) {
      if (c == null || c.isDisposed())
        continue;
      if (--budget[0] < 0)
        break;
      JsonObject o = Core.briefWithChildren(c);
      if (depth > 1 && o.get("children").getAsInt() > 0)
        o.add("nodes", subtree(c, depth - 1, budget));
      arr.add(o);
    }
    return arr;
  }

  static JsonObject detail(TreeNode n, boolean html) {
    JsonObject o = Core.brief(n);
    o.addProperty("uvid", n.getUserVisibleName());
    TreeNode parent = n.getWorkParent();
    if (parent != null)
      o.addProperty("parent", parent.getQualifiedId());
    // the own description (Requirement.getDescription gives the texts of the locations when it has none)
    Attribute da = n.attrs().getAttribute(TreeNode.ATTR_DESCRIPTION);
    String d = da == null || da.getValue() == null ? null : da.getValue().toString();
    if (d != null && !d.isEmpty())
      o.addProperty("description", html ? d : Core.text(d));
    o.add("attributes", Core.attributes(n, html));
    o.add("links", Core.links(n, false));
    o.add("linked_from", Core.links(n, true));
    if (n instanceof Requirement) {
      JsonArray locs = new JsonArray();
      for (Location l : ((Requirement) n).getLocations(false)) {
        if (l == null || l.isDisposed())
          continue;
        JsonObject lo = new JsonObject();
        lo.addProperty("uuid", l.getSUUId());
        Document doc = l.getDocument();
        if (doc != null)
          lo.addProperty("document", doc.getQualifiedId());
        String ld = l.getDescription();
        if (ld != null)
          lo.addProperty("text", Core.text(ld));
        locs.add(lo);
      }
      o.add("locations", locs);
    }
    JsonArray problems = new JsonArray();
    IMark[] marks = n.getCheckerProblems();
    if (marks != null)
      for (IMark m : marks)
        problems.add(Core.problem(m, n));
    o.add("problems", problems);
    JsonArray children = new JsonArray();
    for (TreeNode c : n.getSortedWorkChildren())
      if (c != null && !c.isDisposed())
        children.add(Core.briefWithChildren(c));
    o.add("children", children);
    JsonArray types = new JsonArray();
    // the types create_node makes (documents come with import_document)
    for (String t : new String[] { Requirement.TYPE_NAME, TestPurpose.TYPE_NAME, "Comment", "TextNode", "DocFolder", "ReportFolder" })
      if (n.getTreeDB().getTreeLogic().canBeCreatedIn(t, n))
        types.add(t);
    o.add("child_types", types);
    return o;
  }

  /** Sets the name, description and attributes of the arguments on a node (in a transaction). */
  private static void edit(TreeNode n, JsonObject a) {
    if (a.has("name") && !a.get("name").isJsonNull())
      n.setName(a.get("name").getAsString());
    if (a.has("description") && !a.get("description").isJsonNull())
      n.setDescription(html(a.get("description").getAsString()));
    if (a.has("attributes") && a.get("attributes").isJsonObject())
      for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("attributes").entrySet())
        setAttribute(n, e.getKey(), e.getValue());
  }

  /** HTML of a description argument: HTML as it is, text as paragraphs. */
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
      throw new InvalidParams("Attribute " + key + ": system attributes are changed with id, name and description");
    if (!Attribute.checkNameIsValid(key, false))
      throw new InvalidParams("Invalid attribute name " + key);
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
      throw new InvalidParams("Attribute " + key + ": unsupported value " + v);
    }
  }

  private static JsonObject search(Core core, JsonObject a) {
    TreeDB db = core.db(Core.str(a, "project"));
    TreeNode under = Core.node(db, Core.optStr(a, "under", "/"));
    String text = Core.optStr(a, "text", null);
    String attribute = Core.optStr(a, "attribute", null);
    AttributeRequest req;
    if (text != null) {
      req = new AttributeRequest(AttributeRequest.USER_VISIBLE_KEY, AttributeOperator.CONTAINS_SINGLE_SPACE, text, false);
    } else if (attribute != null) {
      AttributeOperator op = AttributeOperator.valueOf(Core.optStr(a, "operator", "CONTAINS"));
      String key = com.unitesk.requality.core.attribute.SystemNames.getOne(db).getSysName(attribute);
      req = new AttributeRequest(key != null ? key : attribute, op, Core.optStr(a, "value", ""), false);
    } else {
      throw new InvalidParams("Give 'text' or 'attribute'");
    }
    String type = Core.optStr(a, "type", null);
    int limit = Core.optInt(a, "limit", 100);
    NodeSearch search = new NodeSearch(req);
    search.execute(under);
    JsonArray out = new JsonArray();
    int total = 0;
    for (Pair<? extends TreeNode, List<String>> m : search.getMatches()) {
      TreeNode n = m.getFirst();
      if (n == null || n.isDisposed() || (type != null && !type.equals(n.getType())))
        continue;
      total++;
      if (out.size() >= limit)
        continue;
      JsonObject o = Core.brief(n);
      JsonArray keys = new JsonArray();
      if (m.getSecond() != null)
        m.getSecond().forEach(keys::add);
      o.add("matched", keys);
      out.add(o);
    }
    JsonObject r = obj("matches", out);
    r.addProperty("total", total);
    return r;
  }

  static Document document(TreeNode n) {
    if (!(n instanceof Document))
      throw new ToolError(n.getQualifiedId() + " is not a document (a " + n.getType() + ")");
    return (Document) n;
  }

  /** The text of a document in which the markup of its locations gives character ranges (DocStringRepresentation). */
  static String documentText(Document doc) throws Exception {
    SAXTextExtractor ste = new SAXTextExtractor();
    ste.setNoTransactionsFlag(true);
    ste.setShowMessages(false, false);
    ste.process(doc, doc.getTreeDB().getNode(Requirement.getTypeRootQId()), null, null, null);
    return ste.getText();
  }

  /** Runs of white space as one space, or as one line break when they contain one. */
  static String compact(String s) {
    StringBuilder b = new StringBuilder(s.length());
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (!Character.isWhitespace(c) && c != '\u00a0') {
        b.append(c);
        i++;
        continue;
      }
      boolean newline = false;
      while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == '\u00a0'))
        newline |= s.charAt(i++) == '\n';
      b.append(newline ? '\n' : ' ');
    }
    return b.toString();
  }

  static boolean overlaps(Location l, int from, int to) {
    if (l.attrs().getListAttributeValue(Location.ATTR_MARKUP) == null)
      return false;
    for (int[] f : l.getFragments())
      if (f[0] < to && f[1] > from)
        return true;
    return false;
  }

  static JsonObject location(Location l, String text, boolean raw) {
    JsonObject o = new JsonObject();
    o.addProperty("uuid", l.getSUUId());
    JsonArray ranges = new JsonArray();
    StringBuilder marked = new StringBuilder();
    // a location converted from an old format can have no markup; old catalogs repeat fragments
    List<int[]> fragments = l.attrs().getListAttributeValue(Location.ATTR_MARKUP) == null ? new ArrayList<>() : l.getFragments();
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (int[] f : fragments) {
      if (!seen.add(f[0] + "|" + f[1]))
        continue;
      JsonArray r = new JsonArray();
      r.add(f[0]);
      r.add(f[1]);
      ranges.add(r);
      if (f[0] >= 0 && f[1] <= text.length() && f[0] <= f[1]) {
        if (marked.length() > 0)
          marked.append(" … ");
        marked.append(text, f[0], f[1]);
      }
    }
    o.add("ranges", ranges);
    o.addProperty("text", raw ? marked.toString() : marked.toString().replaceAll("[\\s\\u00a0]+", " ").trim());
    JsonArray reqs = new JsonArray();
    for (Requirement r : l.getRequirements(false, true))
      if (r != null)
        reqs.add(r.getQualifiedId());
    o.add("requirements", reqs);
    return o;
  }

  private static JsonObject link(Core core, JsonObject a, boolean add) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    TreeNode from = Core.node(db, Core.str(a, "from"));
    String toRef = Core.str(a, "to");
    TreeNode to;
    String raw;
    int sep = toRef.indexOf("::");
    if (sep > 0) {
      TreeDB other = core.db(toRef.substring(0, sep));
      to = Core.node(other, toRef.substring(sep + 2));
      raw = other.getLocalName() + "::" + to.getUserVisibleName(true);
    } else {
      to = Core.node(db, toRef);
      raw = to.getUserVisibleName(true);
    }
    String attr = Core.optStr(a, "attribute", db.getDefReferenceName());
    if (attr.startsWith("_"))
      throw new InvalidParams("Attribute " + attr + ": system attributes cannot be links");
    final TreeNode target = to;
    Core.write(db, (add ? "Add link to " : "Remove link to ") + to.getUserVisibleName(), () -> {
      Attribute cur = from.attrs().getAttribute(attr);
      List<Object> raws = new LinkedList<>();
      if (cur != null) {
        if (!(cur.getDefinition().getValueProvider() instanceof ReferenceValueProvider))
          throw new ToolError("Attribute " + attr + " of " + from.getQualifiedId() + " is not a reference attribute");
        Object v = cur.getValue();
        if (v instanceof ListValue) {
          for (Object o : ((ListValue) v).getValues())
            raws.add(o instanceof ReferenceValue ? ((ReferenceValue) o).getRaw() : String.valueOf(o));
        } else if (v instanceof ReferenceValue) {
          raws.add(((ReferenceValue) v).getRaw());
        }
      }
      boolean changed = false;
      if (add) {
        boolean present = false;
        for (Object o : raws)
          present |= pointsTo(from, o.toString(), target);
        if (!present) {
          raws.add(raw);
          changed = true;
        }
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
      return null;
    });
    JsonObject r = Core.brief(from);
    r.add("links", Core.links(from, false));
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

  private static JsonObject createLocation(Core core, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    Document doc = document(Core.node(db, Core.str(a, "document")));
    TreeNode reqNode = Core.node(db, Core.str(a, "requirement"));
    if (!(reqNode instanceof Requirement))
      throw new ToolError(reqNode.getQualifiedId() + " is not a requirement");
    Requirement req = (Requirement) reqNode;
    String text = documentText(doc);
    int start;
    int end;
    String quote = Core.optStr(a, "quote", null);
    if (quote != null) {
      // the words of the quote with any white space (or none, between block elements) between them
      StringBuilder re = new StringBuilder();
      for (String w : quote.trim().split("[\\s\\u00a0]+")) {
        if (re.length() > 0)
          re.append("[\\s\\u00a0]*");
        re.append(java.util.regex.Pattern.quote(w));
      }
      if (re.length() == 0)
        throw new InvalidParams("Empty quote");
      List<int[]> found = new ArrayList<>();
      java.util.regex.Matcher m = java.util.regex.Pattern.compile(re.toString()).matcher(text);
      while (m.find())
        found.add(new int[] { m.start(), m.end() });
      if (found.isEmpty())
        throw new ToolError("The quote does not occur in the text of " + doc.getQualifiedId() + " (see get_document_text)");
      int occ = Core.optInt(a, "occurrence", 0);
      if (occ == 0 && found.size() > 1)
        throw new ToolError("The quote occurs " + found.size() + " times; give 'occurrence' (1 to " + found.size() + ")");
      if (occ > found.size())
        throw new ToolError("The quote occurs only " + found.size() + " times");
      start = found.get(Math.max(occ, 1) - 1)[0];
      end = found.get(Math.max(occ, 1) - 1)[1];
    } else {
      if (!a.has("start") || !a.has("end"))
        throw new InvalidParams("Give 'quote', or 'start' and 'end'");
      start = a.get("start").getAsInt();
      end = a.get("end").getAsInt();
      if (start < 0 || end > text.length() || start >= end)
        throw new ToolError("Invalid range " + start + ".." + end + " (the text has " + text.length() + " characters)");
    }
    final int s = start;
    final int e = end;
    Location loc = Core.write(db, "Create location", () -> {
      Location l = doc.createLocation(null);
      List<Object> frags = new ArrayList<>(Arrays.asList((Object) (s + Location.FRAGMENT_SEPARATOR + e)));
      l.attrs().setConstAttribute(Location.ATTR_MARKUP, new ListValue(AttributeType.STRING, frags), Flag.HIDDEN);
      l.setDescription("<p>" + escape(compact(text.substring(s, e)).trim()) + "</p>");
      l.commitAttributes();
      req.addLocation(l.getUUId());
      req.commitAttributes();
      return l;
    });
    return location(loc, text, false);
  }

  private static JsonObject importDocument(Core core, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    File file = new File(Core.str(a, "file"));
    if (!file.isFile())
      throw new ToolError("No file " + file);
    TreeNode folder = Core.node(db, Core.optStr(a, "folder", Document.getTypeRootQId()));
    List<String> errors = new ArrayList<>();
    ImportDocument.IDocStatusConsumer status = new ImportDocument.IDocStatusConsumer() {
      Throwable error;

      @Override
      public void updateStatus(int code, String message, Exception e) {
        if (code != 0) {
          errors.add(message + (e != null ? ": " + e : ""));
          if (e != null)
            error = e;
        }
      }

      @Override
      public Boolean getStatusOk() {
        return errors.isEmpty();
      }

      @Override
      public Throwable getStatusException() {
        return error;
      }
    };
    ImportDocument id = ImportDocument.getOne(false, status, folder, file.getParentFile());
    List<File> files = Arrays.asList(file);
    Document doc = Core.write(db, "Import documents", () -> {
      Map<String, String> fn = new HashMap<>();
      Map<String, Document> dc = new HashMap<>();
      id.importFilesFirst(dc, fn, files, folder, NullProgressMonitor.ONE, 1);
      return id.importFilesSecond(dc, fn, files, folder, NullProgressMonitor.ONE, 1);
    });
    if (doc == null || !errors.isEmpty())
      throw new ToolError("Import of " + file + " failed: " + errors);
    JsonObject r = Core.brief(doc);
    r.addProperty("text_length", documentText(doc).length());
    return r;
  }

  private static JsonObject generateReport(Core core, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    String templateId = Core.str(a, "template");
    Template tpl = TemplatesManager.getOne(db).getTemplates().get(templateId);
    if (tpl == null)
      throw new ToolError("No template " + templateId + " (see list_report_templates)");
    File output = new File(Core.str(a, "output"));
    output.mkdirs();
    if (!output.isDirectory())
      throw new ToolError("Cannot create the folder " + output);
    String root = Core.node(db, Core.optStr(a, "root", Requirement.getTypeRootQId())).getQualifiedId();
    TreeNode reports = Core.node(db, "/Reports");
    // the temporary report settings are not changes of the user: not in the undo history
    ReportSettings rs = Core.write(db, "Create report settings", false, () -> {
      ReportSettings s = (ReportSettings) reports.createChild(RequalityCLI.getNextId(reports, ReportSettings.TYPE_NAME, "mcp"),
          ReportSettings.TYPE_NAME);
      s.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, tpl.getId(), 0);
      s.attrs().setConstAttribute(ReportSettings.ATTR_ROOT_REQUIREMENT, root, 0);
      s.setCrossProjectFlag(true);
      if (a.has("parameters") && a.get("parameters").isJsonObject())
        for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("parameters").entrySet())
          s.attrs().setConstAttribute(e.getKey(), e.getValue().isJsonNull() ? "" : e.getValue().getAsString(), 0);
      s.commitAttributes();
      return s;
    });
    List<String> files = new ArrayList<>();
    List<TreeNode> generated = new ArrayList<>();
    try {
      IGenerateReportHandler handler = GenerateReportHandler.getOne();
      @SuppressWarnings("unchecked")
      ReportGenResult<TreeNode>[] results = handler.execute(false, db, NullProgressMonitor.ONE, false, rs);
      if (results != null)
        for (ReportGenResult<TreeNode> r : results)
          if (r != null && r.report != null)
            generated.add(r.report);
      if (generated.isEmpty())
        throw new ToolError("The report was not generated (see server_messages)");
      ReportExportHandler.getOne().exportReport(generated, output.getAbsolutePath(), false);
      File[] list = output.listFiles();
      if (list != null)
        for (File f : list)
          files.add(f.getAbsolutePath());
    } finally {
      Core.write(db, "Delete report settings", false, () -> {
        for (TreeNode g : generated)
          if (!g.isDisposed())
            g.delete();
        if (!rs.isDisposed())
          rs.delete();
        return null;
      });
    }
    JsonObject r = new JsonObject();
    r.addProperty("output", output.getAbsolutePath());
    JsonArray fa = new JsonArray();
    files.forEach(fa::add);
    r.add("files", fa);
    return r;
  }

  /** Undo or redo of the workbench operation history, on the UI thread as the Edit > Undo command of Eclipse. */
  private static JsonObject undoRedo(boolean undo) {
    String what = Display.getDefault().syncCall(() -> {
      com.unitesk.requality.vscode.ui.workbench.IWorkbenchOperationSupport os = PlatformUI.getWorkbench().getOperationSupport();
      com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation op = undo
          ? os.getOperationHistory().getUndoOperation(os.getUndoContext())
          : os.getOperationHistory().getRedoOperation(os.getUndoContext());
      if (op == null)
        return null;
      String label = op.getLabel();
      return (undo ? UndoRedoMech.callUndo() : UndoRedoMech.callRedo()) ? label : "";
    });
    if (what == null)
      throw new ToolError(undo ? "Nothing to undo" : "Nothing to redo");
    if (what.isEmpty())
      throw new ToolError((undo ? "Undo" : "Redo") + " failed");
    JsonObject r = new JsonObject();
    r.addProperty(undo ? "undone" : "redone", what);
    return r;
  }

  /** A small builder of the JSON schemas of tool arguments. */
  static final class Schema {
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    static Schema object() {
      return new Schema();
    }

    Schema prop(String name, String type, String description, boolean req) {
      JsonObject p = new JsonObject();
      p.addProperty("type", type);
      p.addProperty("description", description);
      return prop(name, p, req);
    }

    Schema prop(String name, JsonObject schema, boolean req) {
      properties.add(name, schema);
      if (req)
        required.add(name);
      return this;
    }

    JsonObject build() {
      JsonObject s = new JsonObject();
      s.addProperty("type", "object");
      s.add("properties", properties);
      if (required.size() > 0)
        s.add("required", required);
      return s;
    }
  }
}
