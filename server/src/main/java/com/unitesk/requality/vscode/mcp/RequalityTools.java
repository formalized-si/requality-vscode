package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.GenerateReportHandler;
import com.unitesk.requality.core.operation.IGenerateReportHandler;
import com.unitesk.requality.core.operation.ImportDocument;
import com.unitesk.requality.core.operation.NullProgressMonitor;
import com.unitesk.requality.core.operation.ReportExportHandler;
import com.unitesk.requality.core.operation.ReportGenResult;
import com.unitesk.requality.core.operation.mass.NodeSearch;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.core.search.AttributeOperator;
import com.unitesk.requality.core.search.AttributeRequest;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateParamDefinition;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.mcp.McpServer.Tool;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * The MCP tools of Requality, made for the jobs of an agent: read a document and turn its text into requirements and
 * locations (read_document, apply_changes), keep the traceability when a new document version comes
 * (update_document_version), tell the coverage (trace_coverage), and let the person review and undo what the agent
 * changed (review_changes, undo).
 */
final class RequalityTools {
  private RequalityTools() {
  }

  private static final String PROJECT = "Project name (see list_projects).";
  private static final String NODE = "Node: its UUID or its path in the project, for example /Requirements/A/B.";
  /** get_node lists at most this many children (get_tree pages larger ones). */
  private static final int MAX_CHILDREN = 100;

  static void register(McpServer mcp, HeadlessClient headless) {
    Core core = new Core(headless);
    Journal journal = new Journal(headless.server().options().storage);

    // ---- orientation

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
          JsonObject res = obj("projects", out);
          if (out.isEmpty())
            res.addProperty("hint", "No Requality catalog in the folders of the server. Create one with create_project, or "
                + "start requality-mcp with the folder that contains the catalogs.");
          return res;
        }));

    mcp.add(new Tool("get_tree", "Get tree",
        "The subtree of a node: the children (id, name, type, number of children; the locations of a document as a "
            + "number) to the given depth. The path of a child is the path of its parent, \"/\" and its id. The top "
            + "folders of a project are /Requirements, /Documents, /Reports, /Types and /Templates.",
        Schema.object().prop("project", "string", PROJECT, true).prop("node", "string", NODE + " Default: the project root.", false)
            .prop("depth", "integer", "Levels of children (1 to 10, default 2).", false)
            .prop("max_nodes", "integer", "At most this many nodes (default 200).", false).build(),
        true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          TreeNode root = Core.node(db, Core.optStr(a, "node", "/"));
          int depth = Math.max(1, Math.min(10, Core.optInt(a, "depth", 2)));
          int[] budget = { Core.optInt(a, "max_nodes", 200) };
          JsonObject o = Core.briefWithChildren(root);
          o.add("nodes", subtree(root, depth, budget));
          if (budget[0] < 0) {
            o.addProperty("truncated", true);
            o.addProperty("hint", "More nodes than max_nodes: ask for a subtree (node), a smaller depth, or use search_nodes "
                + "and trace_coverage.");
          }
          return o;
        }));

    mcp.add(new Tool("get_node", "Get node",
        "All about one node: id, name, type, description, user attributes, outgoing and incoming links, the document "
            + "locations of a requirement (with the marked text), checker problems, parent, children, and the node "
            + "types apply_changes can create in it.",
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

    // ---- documents to requirements

    mcp.add(new Tool("read_document", "Read document",
        "Reads a document of the project (a node under /Documents) paragraph by paragraph. Each paragraph has its "
            + "number 'p', its character range, its text, its coverage by requirement locations (none, partial, full) and "
            + "the requirements that mark it. Use 'filter': 'uncovered' to see only the text that no requirement covers "
            + "yet: the work list when you turn a document into requirements with apply_changes (op 'locate').",
        Schema.object().prop("project", "string", PROJECT, true).prop("document", "string", "The document: " + NODE, true)
            .prop("from", "integer", "First paragraph number (default 0).", false)
            .prop("count", "integer", "Number of paragraphs (default 200).", false)
            .prop("filter", enumSchema("Which paragraphs (default all).", "all", "uncovered", "partial", "covered"), false)
            .prop("ranges", "boolean", "Give the character range of each paragraph (for locate with start and end; default false).", false)
            .build(),
        true, false, a -> readDocument(core, a)));

    mcp.add(new Tool("apply_changes", "Apply changes",
        "Changes a project with a list of operations in one transaction: all of them or none (an error names the "
            + "operation and changes nothing), one undo step, one entry in review_changes. 'dry_run' checks the "
            + "operations and changes nothing. Operations ('op'):\n"
            + "- create: parent, type (default Requirement; TestPurpose, Comment, TextNode, DocFolder, ReportFolder), id "
            + "(default: the next free id), name, description, attributes\n"
            + "- update: node, id, name, description, attributes (a value null removes the attribute)\n"
            + "- delete: node (with the nodes under it)\n"
            + "- move: node, parent, after (a child of the new parent; default: last)\n"
            + "- link / unlink: from, to (\"<project>::<path>\" for another project), attribute (default: the default link attribute)\n"
            + "- locate: requirement, document, and 'quote' (text of the document; white space does not matter; 'paragraph' "
            + "or 'occurrence' when it occurs more than once) or 'paragraph' alone (all its text) or 'start' and 'end'\n"
            + "- unlocate: location (its UUID), requirement (only this requirement; default: delete the location)\n"
            + "- resolve: item (a review item of trace_coverage or update_document_version), status (COMPLETED, default, or CHECK)\n"
            + "Name a created node with 'as' and refer to it later in the same call as \"$name\". Descriptions are text "
            + "(paragraphs separated by empty lines) or HTML (starts with '<'). Attribute values: string, number, boolean, "
            + "list of strings.",
        Schema.object().prop("project", "string", PROJECT, true).prop("operations", operationsSchema(), true)
            .prop("summary", "string", "What the change does and why, for the person who reviews it.", false)
            .prop("dry_run", "boolean", "Check the operations, change nothing (default false).", false).build(),
        false, true, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          if (!a.has("operations") || !a.get("operations").isJsonArray())
            throw new InvalidParams("Missing array argument operations");
          JsonArray ops = a.getAsJsonArray("operations");
          String summary = Core.optStr(a, "summary", null);
          boolean dry = Core.optBool(a, "dry_run", false);
          String label = "Agent: " + (summary != null ? (summary.length() > 60 ? summary.substring(0, 57) + "..." : summary)
              : ops.size() + " operation" + (ops.size() == 1 ? "" : "s"));
          JsonArray results = Changes.apply(db, ops, label, dry);
          JsonObject r = new JsonObject();
          if (dry) {
            r.addProperty("dry_run", true);
            r.addProperty("note", "Nothing was changed. The operations can be applied.");
          } else {
            r.addProperty("change", journal.add(db.getSemiProjectName(), "apply_changes", label, summary, results).get("change").getAsInt());
          }
          r.add("results", results);
          return r;
        }));

    mcp.add(new Tool("import_document", "Import document",
        "Imports a document file (HTML, XHTML, DOC, DOCX, ODT, PDF) into the project as a Document under a document "
            + "folder. Then read it with read_document.",
        Schema.object().prop("project", "string", PROJECT, true).prop("file", "string", "Absolute path of the file.", true)
            .prop("folder", "string", "The document folder: " + NODE + " Default: /Documents.", false).build(),
        false, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          Document doc = importDocument(db, new File(Core.str(a, "file")), Core.node(db, Core.optStr(a, "folder", Document.getTypeRootQId())));
          DocumentText dt = DocumentText.of(doc);
          JsonObject r = Core.brief(doc);
          r.addProperty("paragraphs", dt.paragraphs.size());
          r.addProperty("text_length", dt.text.length());
          JsonArray ops = new JsonArray();
          JsonObject op = Core.brief(doc);
          op.addProperty("op", "import");
          op.addProperty("file", Core.str(a, "file"));
          ops.add(op);
          r.addProperty("change", journal.add(db.getSemiProjectName(), "import_document", "Import documents", null, ops).get("change").getAsInt());
          return r;
        }));

    mcp.add(new Tool("update_document_version", "Update document version",
        "A new version of a specification. Call it first: its result is the list of what to fix. It moves the "
            + "requirement locations of the old document to the new document, "
            + "sentence by sentence, in one undo step. For each requirement: unchanged (its text is in the new version), "
            + "changed (moved to the most similar sentence; old and new text, similarity, the new location UUID to unlocate "
            + "when wrong) or lost (with suggestions). Lost and changed come first, to review. Also lists the statements of "
            + "the new version that no requirement marks (uncovered_statements). The old document and its locations stay. "
            + "Give the new version as a file (imported next to the old document) or as a document already imported.",
        Schema.object().prop("project", "string", PROJECT, true).prop("document", "string", "The old document: " + NODE, true)
            .prop("file", "string", "Absolute path of the new version.", false)
            .prop("new_document", "string", "The new version, already imported: " + NODE, false)
            .prop("detach_old", "boolean", "Remove the locations of the old document from the requirements, so that they "
                + "trace to the new version only (the old document stays as the history; default false).", false).build(),
        false, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          Document oldDoc = DocumentText.document(Core.node(db, Core.str(a, "document")));
          Document newDoc;
          String file = Core.optStr(a, "file", null);
          if (file != null) {
            newDoc = importDocument(db, new File(file), oldDoc.getWorkParent());
            JsonArray ops = new JsonArray();
            JsonObject op = Core.brief(newDoc);
            op.addProperty("op", "import");
            op.addProperty("file", file);
            ops.add(op);
            journal.add(db.getSemiProjectName(), "update_document_version", "Import documents", null, ops);
          } else if (a.has("new_document")) {
            newDoc = DocumentText.document(Core.node(db, Core.str(a, "new_document")));
          } else {
            throw new InvalidParams("Give 'file' or 'new_document'");
          }
          JsonObject r = SpecUpdate.run(db, oldDoc, newDoc, Core.optBool(a, "detach_old", false));
          JsonArray ops = new JsonArray();
          JsonObject op = new JsonObject();
          op.addProperty("op", "update_version");
          op.addProperty("old_document", oldDoc.getQualifiedId());
          op.addProperty("new_document", newDoc.getQualifiedId());
          op.add("summary", r.get("summary"));
          JsonArray created = new JsonArray();
          for (JsonElement q : r.getAsJsonArray("requirements"))
            for (JsonElement l : q.getAsJsonObject().getAsJsonArray("locations"))
              if (l.getAsJsonObject().has("new_location") && !created.contains(l.getAsJsonObject().get("new_location")))
                created.add(l.getAsJsonObject().get("new_location"));
          op.add("new_locations", created);
          if (Core.optBool(a, "detach_old", false)) {
            // the requirements and the old locations they lost: a revert attaches them again
            JsonArray detached = new JsonArray();
            for (JsonElement q : r.getAsJsonArray("requirements"))
              for (JsonElement l : q.getAsJsonObject().getAsJsonArray("locations")) {
                JsonArray pair = new JsonArray();
                pair.add(q.getAsJsonObject().get("uuid"));
                pair.add(l.getAsJsonObject().get("old_location"));
                detached.add(pair);
              }
            op.add("detached", detached);
          }
          ops.add(op);
          r.addProperty("change", journal.add(db.getSemiProjectName(), "update_document_version", "Update document version", null, ops).get("change").getAsInt());
          return r;
        }));

    // ---- traceability and review

    mcp.add(new Tool("trace_coverage", "Trace coverage",
        "The traceability of a project as data: requirements without document locations or without test purposes "
            + "(leaf requirements), links that do not resolve, checker problems, the coverage of each document by "
            + "locations, and the open review items of document updates.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("under", "string", "Only the requirements under this node (default /Requirements).", false)
            .prop("refresh", "boolean", "Run the checkers again first (default false).", false)
            .prop("documents", "boolean", "Include the coverage of the documents (default true).", false)
            .prop("limit", "integer", "At most this many items in each list (default 50).", false).build(),
        true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          return Coverage.report(db, Core.node(db, Core.optStr(a, "under", Requirement.getTypeRootQId())),
              Core.optBool(a, "refresh", false), Core.optBool(a, "documents", true), Core.optInt(a, "limit", 50));
        }));

    mcp.add(new Tool("review_changes", "Review changes",
        "What the agent changed, newest first, also in earlier sessions: each change (apply_changes, import_document, "
            + "update_document_version) with its time, summary, operations, the values before and after, and its state "
            + "(done or undone). undo reverts the newest change of this session that is done.",
        Schema.object().prop("project", "string", "Only the changes of this project.", false)
            .prop("last", "integer", "At most this many changes (default 20).", false).build(),
        true, false, a -> {
          String project = Core.optStr(a, "project", null);
          int last = Core.optInt(a, "last", 20);
          List<JsonObject> all = journal.entries();
          JsonArray out = new JsonArray();
          for (int i = all.size() - 1; i >= 0 && out.size() < last; i--)
            if (project == null || project.equals(all.get(i).get("project").getAsString()))
              out.add(withoutRaw(all.get(i).deepCopy()));
          JsonObject r = obj("changes", out);
          r.addProperty("total", all.size());
          return r;
        }));

    mcp.add(new Tool("revert_change", "Revert change",
        "Reverts a change of review_changes by its number, also a change of an earlier session (undo knows only the "
            + "changes of this session): the inverse of its operations, in one step. Deleted nodes come back with new "
            + "UUIDs. The result tells for each operation whether it was reverted.",
        Schema.object().prop("change", "integer", "The change number (see review_changes).", true).build(), false, true, a -> {
          int n = a.get("change").getAsInt();
          JsonObject e = journal.entry(n);
          if (e == null)
            throw new ToolError("No change " + n + " (see review_changes)");
          String state = e.get("state").getAsString();
          if (!"done".equals(state))
            throw new ToolError("Change " + n + " is " + state + " already");
          TreeDB db = core.db(e.get("project").getAsString());
          String label = "Revert change " + n;
          JsonArray notes = Changes.revert(db, e.getAsJsonArray("operations"), label);
          JsonObject entry = journal.add(db.getSemiProjectName(), "revert_change", label, "Revert of change " + n, notes);
          entry.addProperty("reverts", n);
          journal.setState(n, "reverted");
          JsonObject r = new JsonObject();
          r.addProperty("change", entry.get("change").getAsInt());
          r.addProperty("reverted_change", n);
          r.add("operations", notes);
          return r;
        }));

    mcp.add(new Tool("undo", "Undo", "Undoes the newest change (of this server, in any project) and marks it undone in review_changes.",
        Schema.object().build(), false, false, a -> undoRedo(journal, true)));
    mcp.add(new Tool("redo", "Redo", "Redoes the newest undone change.", Schema.object().build(), false, false,
        a -> undoRedo(journal, false)));

    // ---- reports and projects

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

    mcp.add(new Tool("generate_report", "Generate report",
        "Generates a report from a template (see list_report_templates) for the requirements under a node and writes it "
            + "to a folder, for example to show it to a person.",
        Schema.object().prop("project", "string", PROJECT, true).prop("template", "string", "Template id.", true)
            .prop("output", "string", "Absolute path of the output folder.", true)
            .prop("root", "string", "Requirements under this node (default /Requirements).", false)
            .prop("parameters", paramsSchema(), false).build(),
        false, false, a -> generateReport(core, a)));

    mcp.add(new Tool("open_project", "Open project", "Opens a closed project (see list_projects) and waits until it is loaded.",
        Schema.object().prop("project", "string", PROJECT, true).build(), false, false, a -> {
          String name = Core.str(a, "project");
          headless.server().workspace().openProject(core.project(name).dir);
          core.db(name);
          return obj("project", new JsonPrimitive(name));
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
          return obj("project", new JsonPrimitive(name));
        }));

    mcp.add(new Tool("server_messages", "Server messages",
        "The messages and questions the Requality core showed (for example the result of a format conversion) and "
            + "the answers the headless server gave, oldest first.",
        Schema.object().build(), true, false, a -> {
          JsonArray out = new JsonArray();
          headless.messages().forEach(out::add);
          return obj("messages", out);
        }));

    RequalityPrompts.register(mcp);
  }

  // ---- helpers

  /** An entry of the journal without the saved values for a revert ("raw"). */
  static JsonElement withoutRaw(JsonElement e) {
    if (e.isJsonObject()) {
      JsonObject o = e.getAsJsonObject();
      o.remove("raw");
      for (String k : new ArrayList<>(o.keySet()))
        withoutRaw(o.get(k));
    } else if (e.isJsonArray()) {
      for (JsonElement x : e.getAsJsonArray())
        withoutRaw(x);
    }
    return e;
  }

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

  static JsonObject paramsSchema() {
    JsonObject s = new JsonObject();
    s.addProperty("type", "object");
    s.addProperty("description", "Template parameters: name -> value.");
    return s;
  }

  static JsonObject operationsSchema() {
    JsonObject item = new JsonObject();
    item.addProperty("type", "object");
    JsonObject p = new JsonObject();
    p.add("op", enumSchema("The operation.", "create", "update", "delete", "move", "link", "unlink", "locate", "unlocate", "resolve"));
    for (String k : new String[] { "parent", "node", "from", "to", "requirement", "document", "location", "item", "status", "after",
        "type", "id", "name", "description", "attribute", "quote", "as" }) {
      JsonObject s = new JsonObject();
      s.addProperty("type", "string");
      p.add(k, s);
    }
    for (String k : new String[] { "paragraph", "occurrence", "start", "end" }) {
      JsonObject s = new JsonObject();
      s.addProperty("type", "integer");
      p.add(k, s);
    }
    JsonObject attrs = new JsonObject();
    attrs.addProperty("type", "object");
    attrs.addProperty("description", "User attributes: name -> value (null removes it).");
    p.add("attributes", attrs);
    item.add("properties", p);
    JsonArray req = new JsonArray();
    req.add("op");
    item.add("required", req);
    JsonObject s = new JsonObject();
    s.addProperty("type", "array");
    s.addProperty("description", "The operations, made in this order.");
    s.add("items", item);
    return s;
  }

  private static JsonArray subtree(TreeNode n, int depth, int[] budget) {
    JsonArray arr = new JsonArray();
    for (TreeNode c : n.getSortedWorkChildren()) {
      if (c == null || c.isDisposed() || c instanceof Location)
        continue;
      if (--budget[0] < 0)
        break;
      // the path and the UUID follow from the parent and are left out: a tree of many nodes stays short
      JsonObject o = Core.briefWithChildren(c);
      o.remove("uuid");
      o.remove("path");
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
          lo.addProperty("text", Core.text(ld).replaceAll("\\s+", " "));
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
    // the locations of a document are its children: read_document shows them with their text
    JsonArray children = new JsonArray();
    int total = 0;
    int locations = 0;
    for (TreeNode c : n.getSortedWorkChildren()) {
      if (c == null || c.isDisposed())
        continue;
      if (c instanceof Location) {
        locations++;
        continue;
      }
      if (++total <= MAX_CHILDREN)
        children.add(Core.briefWithChildren(c));
    }
    o.add("children", children);
    if (total > MAX_CHILDREN)
      o.addProperty("children_total", total);
    if (locations > 0)
      o.addProperty("locations_count", locations);
    JsonArray types = new JsonArray();
    // the types apply_changes creates (documents come with import_document)
    for (String t : new String[] { Requirement.TYPE_NAME, TestPurpose.TYPE_NAME, "Comment", "TextNode", "DocFolder", "ReportFolder" })
      if (n.getTreeDB().getTreeLogic().canBeCreatedIn(t, n))
        types.add(t);
    o.add("child_types", types);
    return o;
  }

  private static JsonObject readDocument(Core core, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    DocumentText dt = DocumentText.of(DocumentText.document(Core.node(db, Core.str(a, "document"))));
    String filter = Core.optStr(a, "filter", "all");
    if (!Arrays.asList("all", "uncovered", "partial", "covered").contains(filter))
      throw new InvalidParams("filter: all, uncovered, partial or covered");
    int from = Math.max(0, Core.optInt(a, "from", 0));
    int count = Math.max(1, Core.optInt(a, "count", 200));
    boolean ranges = Core.optBool(a, "ranges", false);
    JsonArray ps = new JsonArray();
    Integer next = null;
    for (int i = from; i < dt.paragraphs.size(); i++) {
      DocumentText.Paragraph p = dt.paragraphs.get(i);
      String c = p.coverage();
      boolean match = filter.equals("all") || (filter.equals("uncovered") && c.equals("none"))
          || (filter.equals("partial") && c.equals("partial")) || (filter.equals("covered") && c.equals("full"));
      if (!match)
        continue;
      if (ps.size() >= count) {
        next = i;
        break;
      }
      JsonObject pj = dt.paragraph(p);
      if (!ranges) {
        pj.remove("start");
        pj.remove("end");
      }
      ps.add(pj);
    }
    JsonObject r = Core.brief(dt.doc);
    r.addProperty("paragraphs_total", dt.paragraphs.size());
    r.addProperty("locations", dt.locations.size());
    int[] t = dt.coverageTotals();
    r.addProperty("covered_text_percent", t[1] == 0 ? 0 : Math.round(t[0] * 1000.0 / t[1]) / 10.0);
    r.addProperty("filter", filter);
    r.add("paragraphs", ps);
    if (next != null)
      r.addProperty("next_from", next);
    return r;
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

  /** Imports a document file into a document folder (one transaction, undo label "Import documents"). */
  static Document importDocument(TreeDB db, File file, TreeNode folder) throws Exception {
    if (!file.isFile())
      throw new ToolError("No file " + file);
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
    return doc;
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
  private static JsonObject undoRedo(Journal journal, boolean undo) {
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
    JsonObject e = journal.mark(what, undo);
    if (e != null)
      r.addProperty("change", e.get("change").getAsInt());
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
