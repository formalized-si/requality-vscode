package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
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
import com.unitesk.requality.core.search.AttributeOperator;
import com.unitesk.requality.core.search.AttributeRequest;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateParamDefinition;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.mcp.McpServer.Tool;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * The MCP tools of Requality, made for the jobs of an agent: read a document and turn its text into requirements and
 * locations (read_document, apply_changes), keep the traceability when a new document version comes
 * (update_document_version), tell the coverage (trace_coverage), and let the person review, undo and revert what the
 * agent changed (review_changes, undo, revert_change).
 */
final class RequalityTools {
  private RequalityTools() {
  }

  private static final String PROJECT = "Project name (see list_projects).";
  private static final String NODE = "Node: its UUID or its path in the project, for example /Requirements/A/B.";
  /** get_node lists at most this many children (get_tree pages larger ones). */
  private static final int MAX_CHILDREN = 100;

  static void register(McpServer mcp, HeadlessClient headless, Policy policy) {
    Core core = new Core(headless);
    Session session = new Session(core, headless.server().options().storage);

    // ---- orientation

    mcp.add(new Tool("list_projects", "List projects",
        "Lists the Requality projects (catalogs) in the workspace folders with their state: loading, ready, broken, closed, "
            + "or needs_conversion (a catalog of an older Requality: convert_project converts it after a backup).",
        Schema.object().build(), true, false, a -> {
          JsonObject r = headless.call("workspace/projects", null).getAsJsonObject();
          JsonArray out = new JsonArray();
          for (JsonElement e : r.getAsJsonArray("projects")) {
            JsonObject p = e.getAsJsonObject();
            JsonObject o = new JsonObject();
            o.add("name", p.get("name"));
            o.add("path", p.get("path"));
            boolean old = headless.needsConversion(p.get("localName").getAsString());
            o.addProperty("state", old ? "needs_conversion" : p.get("state").getAsString());
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
          int[] budget = { Math.max(1, Core.optInt(a, "max_nodes", 200)) };
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
            + "Each match gives the attributes that matched; a document location gives its text and its requirements.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("text", "string", "Text to find in the user-visible attributes (case-insensitive).", false)
            .prop("attribute", "string", "Attribute name, for example status or %Name.", false)
            .prop("operator", enumSchema("Comparison for 'attribute' (default CONTAINS).", OPERATORS), false)
            .prop("value", "string", "Value for 'attribute'.", false)
            .prop("under", "string", NODE + " Search only under this node (default: the project root).", false)
            .prop("type", "string", "Only nodes of this type, for example Requirement or Location.", false)
            .prop("limit", "integer", "At most this many matches (default 100).", false).build(),
        true, false, a -> search(core, a)));

    // ---- documents to requirements

    mcp.add(new Tool("read_document", "Read document",
        "Reads a document of the project (a node under /Documents) paragraph by paragraph. Each paragraph has its "
            + "number 'p', its heading level (h1 to h6, when it is a heading), its text, its coverage by requirement "
            + "locations (none, partial, full) and the requirements that mark it. Use 'filter': 'uncovered' to see only "
            + "the text that no requirement covers yet: the work list when you turn a document into requirements with "
            + "apply_changes (op 'locate').",
        Schema.object().prop("project", "string", PROJECT, true).prop("document", "string", "The document: " + NODE, true)
            .prop("from", "integer", "First paragraph number (default 0).", false)
            .prop("count", "integer", "Number of paragraphs (default 200).", false)
            .prop("filter", enumSchema("Which paragraphs (default all).", FILTERS), false)
            .prop("ranges", "boolean", "Give the character range of each paragraph (for locate with start and end; default false).", false)
            .build(),
        true, false, a -> readDocument(core, a)));

    mcp.add(new Tool("apply_changes", "Apply changes",
        "Changes a project with a list of operations in one transaction: all of them or none (an error names the "
            + "operation and changes nothing), one undo step, one entry in review_changes. 'dry_run' checks the "
            + "operations and changes nothing. Operations ('op') and their keys:\n"
            + "- create: parent, type (Requirement, the default; TestPurpose, Comment, TextNode, DocFolder, ReportFolder), "
            + "id (default: the next free id), name, description, attributes, as\n"
            + "- update: node, id, name, description, attributes (a value null removes the attribute), as\n"
            + "- delete: node (with the nodes under it)\n"
            + "- move: node, parent, after (a child of the new parent; default: last)\n"
            + "- link / unlink: from, to (\"<project>::<path>\" for another project), attribute (default: the default link attribute)\n"
            + "- locate: requirement, document, and 'quote' (text of the document; white space, typographic quotes, dashes "
            + "and letter case do not matter; 'paragraph' or 'occurrence' when it occurs more than once) or 'paragraph' "
            + "alone (all its text) or 'start' and 'end'; as\n"
            + "- unlocate: location (its UUID), requirement (remove the location from this requirement only; default: "
            + "delete the location)\n"
            + "- resolve: item (a review item of trace_coverage), status (COMPLETED, the default, or CHECK)\n"
            + "Name a node with 'as' and refer to it later in the same call as \"$name\". Descriptions are text "
            + "(paragraphs separated by empty lines) or HTML (starts with '<'). Attribute values: string, number, boolean, "
            + "list of strings.",
        Schema.object().prop("project", "string", PROJECT, true).prop("operations", operationsSchema(), true)
            .prop("summary", "string", "What the change does and why, for the person who reviews it.", false)
            .prop("dry_run", "boolean", "Check the operations, change nothing (default false).", false).build(),
        false, true, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          if (!a.get("operations").isJsonArray())
            throw new InvalidParams("Argument operations must be an array of operations");
          JsonArray ops = a.getAsJsonArray("operations");
          Changes.check(ops);
          String summary = Core.optStr(a, "summary", null);
          String label = "Agent: " + (summary != null ? (summary.length() > 60 ? summary.substring(0, 57) + "..." : summary)
              : ops.size() + " operation" + (ops.size() == 1 ? "" : "s"));
          JsonObject r = new JsonObject();
          if (Core.optBool(a, "dry_run", false)) {
            r.add("results", Session.dryRun(db, label, () -> Changes.run(db, ops)));
            r.addProperty("dry_run", true);
            r.addProperty("note", "Nothing was changed. The operations can be applied.");
            return r;
          }
          JsonObject e = session.change(db, "apply_changes", label, summary, () -> Changes.run(db, ops));
          r.addProperty("change", e.get("change").getAsInt());
          r.add("results", e.get("operations"));
          return r;
        }));

    mcp.add(new Tool("import_document", "Import document",
        "Imports a document file (HTML, XHTML, XML, DOC, DOCX, ODT, RTF, PDF, TXT) into the project as a Document under a "
            + "document folder, in one change. Then read it with read_document. The file must be in a folder of the "
            + "server (the folders of the catalogs and the --allow folders).",
        Schema.object().prop("project", "string", PROJECT, true).prop("file", "string", "Absolute path of the file.", true)
            .prop("folder", "string", "The document folder: " + NODE + " Default: /Documents.", false).build(),
        false, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          File file = policy.document(Core.str(a, "file"));
          TreeNode folder = Core.node(db, Core.optStr(a, "folder", Document.getTypeRootQId()));
          String existing = existingName(folder, file);
          Document[] doc = { null };
          JsonObject e = session.change(db, "import_document", "Import documents", "Import of " + file.getName(), () -> {
            doc[0] = importInto(db, file, folder);
            JsonObject op = Core.brief(doc[0]);
            op.addProperty("op", "import");
            op.addProperty("file", file.getPath());
            JsonArray ops = new JsonArray();
            ops.add(op);
            return ops;
          });
          DocumentText dt = DocumentText.of(doc[0]);
          JsonObject r = Core.brief(doc[0]);
          r.addProperty("paragraphs", dt.paragraphs.size());
          r.addProperty("text_length", dt.text.length());
          r.addProperty("change", e.get("change").getAsInt());
          if (existing != null)
            r.addProperty("note", folder.getQualifiedId() + "/" + existing + " existed already: this import is "
                + doc[0].getQualifiedId() + ". For a new version of a document use update_document_version");
          return r;
        }));

    mcp.add(new Tool("update_document_version", "Update document version",
        "A new version of a specification. Call it first: its result is the list of what to fix. It imports the new "
            + "version (or takes one already imported) and moves the requirement locations of the old document to it "
            + "sentence by sentence, in one change (one undo step). For each requirement: unchanged (its text is in the "
            + "new version), changed (moved to the most similar sentence; old and new text, similarity, "
            + "modality_changed when shall/must/may/not changed, numbers_changed, and the new location UUID to unlocate "
            + "when it is wrong) or lost (with suggestions). Lost and changed come first. Also lists the statements of the "
            + "new version that no requirement marks (uncovered_statements). The old document stays as the history; by "
            + "default the requirements lose their locations in it (detach_old).",
        Schema.object().prop("project", "string", PROJECT, true).prop("document", "string", "The old document: " + NODE, true)
            .prop("file", "string", "Absolute path of the new version (in a folder of the server).", false)
            .prop("new_document", "string", "The new version, already imported: " + NODE, false)
            .prop("detach_old", "boolean", "Remove the locations of the old document from the requirements, so that they "
                + "trace to the new version only (default true; false keeps both).", false).build(),
        false, true, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          Document oldDoc = DocumentText.document(Core.node(db, Core.str(a, "document")));
          String path = Core.optStr(a, "file", null);
          String newRef = Core.optStr(a, "new_document", null);
          if ((path == null) == (newRef == null))
            throw new InvalidParams("Give 'file' or 'new_document' (one of them)");
          File file = path == null ? null : policy.document(path);
          TreeNode folder = oldDoc.getWorkParent();
          if (file != null) {
            String existing = existingName(folder, file);
            if (existing != null)
              throw new ToolError(folder.getQualifiedId() + "/" + existing + " exists already (an earlier import or update?): "
                  + "give it as new_document, or delete it first");
          }
          Document given = newRef == null ? null : DocumentText.document(Core.node(db, newRef));
          boolean detach = Core.optBool(a, "detach_old", true);
          JsonObject[] result = { null };
          JsonObject e = session.change(db, "update_document_version", "Update document version",
              "New version of " + oldDoc.getQualifiedId(), () -> {
                JsonArray ops = new JsonArray();
                Document newDoc = given;
                if (file != null) {
                  newDoc = importInto(db, file, folder);
                  JsonObject op = Core.brief(newDoc);
                  op.addProperty("op", "import");
                  op.addProperty("file", file.getPath());
                  ops.add(op);
                }
                result[0] = SpecUpdate.run(db, oldDoc, newDoc, detach);
                JsonObject op = new JsonObject();
                op.addProperty("op", "update_version");
                op.addProperty("path", newDoc.getQualifiedId());
                op.addProperty("old_document", oldDoc.getQualifiedId());
                op.add("summary", result[0].get("summary"));
                ops.add(op);
                return ops;
              });
          result[0].addProperty("change", e.get("change").getAsInt());
          return result[0];
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
              Core.optBool(a, "refresh", false), Core.optBool(a, "documents", true), Math.max(1, Core.optInt(a, "limit", 50)));
        }));

    mcp.add(new Tool("test_coverage", "Test coverage",
        "The coverage of the requirements by tests, from a coverage file of the test runs (XML, the file a coverage "
            + "source of Requality reads): the leaf requirements tests cover (with the tests and hits), the ones no test "
            + "covers, the test failures with the requirements they violate, and the requirements of the file that the "
            + "project does not have. It changes nothing. The file: " + TestCoverage.FORMAT,
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("file", "string", "Absolute path of the coverage file (in a folder of the server).", true)
            .prop("under", "string", "Only the requirements under this node (default /Requirements).", false)
            .prop("limit", "integer", "At most this many items in each list (default 50).", false).build(),
        true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          File file = policy.file(Core.str(a, "file"), "coverage file", java.util.Set.of("xml"));
          return TestCoverage.report(db, Core.node(db, Core.optStr(a, "under", Requirement.getTypeRootQId())), file,
              Math.max(1, Core.optInt(a, "limit", 50)));
        }));

    mcp.add(new Tool("review_changes", "Review changes",
        "What the agent changed, newest first, also in earlier sessions: each change (apply_changes, import_document, "
            + "update_document_version, revert_change) with its number, time, project, summary, kinds of operations, "
            + "first nodes and state (done, undone or reverted). With project and change: all operations of that change "
            + "with the values before and after.",
        Schema.object().prop("project", "string", "Only the changes of this project.", false)
            .prop("change", "integer", "One change (with project): all its operations.", false)
            .prop("verbose", "boolean", "The operations of each change (default false).", false)
            .prop("last", "integer", "At most this many changes (default 20).", false).build(),
        true, false, a -> session.review(Core.optStr(a, "project", null), Math.max(1, Core.optInt(a, "last", 20)),
            a.has("change") && !a.get("change").isJsonNull() ? Core.optInt(a, "change", 0) : null, Core.optBool(a, "verbose", false))));

    mcp.add(new Tool("revert_change", "Revert change",
        "Reverts a change of review_changes, also of an earlier session: its nodes become as they were before it, with "
            + "their UUIDs (deleted nodes come back with their links and locations), in one change. It is refused when a "
            + "node of the change was changed later (by the agent or in VS Code); the error lists them. 'force' reverts "
            + "anyway (the later changes of these nodes are lost); 'dry_run' tells what would be reverted.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("change", "integer", "The change number (see review_changes).", true)
            .prop("dry_run", "boolean", "Tell what the revert does, change nothing (default false).", false)
            .prop("force", "boolean", "Revert also when nodes of the change were changed later (default false).", false).build(),
        false, true, a -> session.revert(core.db(Core.str(a, "project")), Core.optInt(a, "change", 0),
            Core.optBool(a, "force", false), Core.optBool(a, "dry_run", false))));

    mcp.add(new Tool("undo", "Undo",
        "Undoes the newest change of the agent in this session (in any project) and marks it undone in review_changes. "
            + "For a change of an earlier session use revert_change.",
        Schema.object().build(), false, true, a -> session.undoRedo(true)));
    mcp.add(new Tool("redo", "Redo", "Redoes the newest undone change of this session.", Schema.object().build(), false, true,
        a -> session.undoRedo(false)));

    // ---- reports and projects

    mcp.add(new Tool("list_report_templates", "List report templates",
        "The report templates the project can use: id, name, group, description and parameters (name, type, default).",
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
            for (TemplateParamDefinition d : parameters(t)) {
              JsonObject p = new JsonObject();
              p.addProperty("name", d.name);
              p.addProperty("type", paramType(d));
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
            + "to a new or empty folder in a folder of the server, for example to show it to a person.",
        Schema.object().prop("project", "string", PROJECT, true).prop("template", "string", "Template id.", true)
            .prop("output", "string", "Absolute path of the output folder: new or empty.", true)
            .prop("root", "string", "Requirements under this node (default /Requirements).", false)
            .prop("parameters", paramsSchema(), false).build(),
        false, false, a -> generateReport(core, policy, a)));

    mcp.add(new Tool("open_project", "Open project",
        "Opens a closed project, or loads a broken one again (for example after another process released its catalog), "
            + "and waits until it is loaded.",
        Schema.object().prop("project", "string", PROJECT, true).build(), false, false, a -> {
          String name = Core.str(a, "project");
          Workspace.Project p = core.project(name);
          if (p.closed)
            core.workspace().openProject(p.dir);
          else if (p.error != null || com.unitesk.requality.vscode.core.Nodes.findDB(p.localName) == null)
            core.workspace().loadProject(p.dir);
          core.db(name);
          return obj("project", new JsonPrimitive(name));
        }));

    mcp.add(new Tool("close_project", "Close project",
        "Closes a project: its catalog is released for VS Code or another requality-mcp. open_project opens it again.",
        Schema.object().prop("project", "string", PROJECT, true).build(), false, false, a -> {
          String name = Core.str(a, "project");
          Workspace.Project p = core.project(name);
          if (!p.closed)
            core.workspace().closeProject(p.dir);
          return obj("closed", new JsonPrimitive(name));
        }));

    mcp.add(new Tool("convert_project", "Convert project",
        "Converts a project in the catalog format of an older Requality (state needs_conversion) to the current format: "
            + "first a copy of the catalog goes to the backups folder of the server storage, then the catalog is "
            + "converted and loaded. Older Requality versions cannot open it after that.",
        Schema.object().prop("project", "string", PROJECT, true).build(), false, true, a -> {
          String name = Core.str(a, "project");
          Workspace.Project p = core.project(name);
          if (!headless.needsConversion(p.localName))
            throw new ToolError("Project " + name + " needs no conversion");
          File backup = backup(headless.server().options().storage, p.dir);
          headless.allowConversion(p.localName);
          // the cancelled conversion closed the project (as in Eclipse): it opens again and converts
          if (p.closed)
            core.workspace().openProject(p.dir);
          else
            core.workspace().loadProject(p.dir);
          // the conversion starts when the loader thread loads the catalog again
          long end = System.currentTimeMillis() + 30_000;
          while (headless.needsConversion(p.localName) && System.currentTimeMillis() < end)
            Core.sleep(100);
          core.db(name);
          JsonObject r = obj("project", new JsonPrimitive(name));
          r.addProperty("backup", backup.getPath());
          return r;
        }));

    mcp.add(new Tool("create_project", "Create project",
        "Creates a new empty Requality project (catalog) in the first folder of the server.",
        Schema.object().prop("name", "string", "Project name (also the folder name).", true).build(), false, false, a -> {
          String name = Core.str(a, "name");
          try {
            com.unitesk.requality.core.tree.TreeLogic.isIdValidExc(name);
          } catch (Exception e) {
            throw new ToolError("Invalid project name: " + Core.message(e));
          }
          File dir = core.workspace().defaultLocation(name);
          if (dir == null)
            throw new ToolError("The server has no folder for new projects");
          policy.allowed(dir.getAbsolutePath(), "The project folder");
          if (dir.exists())
            throw new ToolError("The folder " + dir + " exists already");
          if (!core.workspace().lock(dir))
            throw new ToolError("Another Requality process holds the catalog " + dir);
          try {
            ResManager.getManager().getTracker().createProject(name, name);
          } catch (RuntimeException e) {
            core.workspace().unlock(dir);
            throw new ToolError("The project " + name + " was not created: " + Core.message(e));
          }
          core.workspace().rescan();
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

  static final String[] OPERATORS = { "EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "EXISTS", "NOT_EXISTS" };
  static final String[] FILTERS = { "all", "uncovered", "partial", "covered" };

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

  static JsonObject paramsSchema() {
    JsonObject s = new JsonObject();
    s.addProperty("type", "object");
    s.addProperty("description", "Template parameters: name -> value (see the parameters of list_report_templates; a "
        + "boolean, a number, a string, or a list of strings).");
    return s;
  }

  /** One schema for each operation (a JSON Schema "oneOf" on 'op'), with the keys the operation takes. */
  static JsonObject operationsSchema() {
    JsonArray variants = new JsonArray();
    for (Map.Entry<String, List<String>> e : Changes.KEYS.entrySet()) {
      JsonObject item = new JsonObject();
      item.addProperty("type", "object");
      JsonObject p = new JsonObject();
      JsonObject op = new JsonObject();
      op.addProperty("const", e.getKey());
      p.add("op", op);
      for (String k : e.getValue()) {
        JsonObject s = new JsonObject();
        switch (k) {
          case "paragraph":
          case "occurrence":
          case "start":
          case "end":
            s.addProperty("type", "integer");
            break;
          case "attributes":
            s.addProperty("type", "object");
            s.addProperty("description", "User attributes: name -> value (null removes it).");
            break;
          default:
            s.addProperty("type", "string");
        }
        p.add(k, s);
      }
      item.add("properties", p);
      JsonArray req = new JsonArray();
      req.add("op");
      item.add("required", req);
      item.addProperty("additionalProperties", false);
      variants.add(item);
    }
    JsonObject item = new JsonObject();
    item.add("oneOf", variants);
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
    if (n instanceof Location) {
      Location l = (Location) n;
      if (l.getDocument() != null)
        o.addProperty("document", l.getDocument().getQualifiedId());
      o.add("requirements", strings(DocumentText.requirements(l)));
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
    for (String t : Changes.TYPES)
      if (n.getTreeDB().getTreeLogic().canBeCreatedIn(t, n))
        types.add(t);
    o.add("child_types", types);
    return o;
  }

  private static JsonArray strings(List<String> l) {
    JsonArray a = new JsonArray();
    l.forEach(a::add);
    return a;
  }

  private static JsonObject readDocument(Core core, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    String filter = Core.optEnum(a, "filter", "all", FILTERS);
    DocumentText dt = DocumentText.of(DocumentText.document(Core.node(db, Core.str(a, "document"))));
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

  private static JsonObject search(Core core, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    TreeNode under = Core.node(db, Core.optStr(a, "under", "/"));
    String text = Core.optStr(a, "text", null);
    String attribute = Core.optStr(a, "attribute", null);
    AttributeRequest req;
    if (text != null) {
      req = new AttributeRequest(AttributeRequest.USER_VISIBLE_KEY, AttributeOperator.CONTAINS_SINGLE_SPACE, text, false);
    } else if (attribute != null) {
      AttributeOperator op = AttributeOperator.valueOf(Core.optEnum(a, "operator", "CONTAINS", OPERATORS));
      String key = com.unitesk.requality.core.attribute.SystemNames.getOne(db).getSysName(attribute);
      req = new AttributeRequest(key != null ? key : attribute, op, Core.optStr(a, "value", ""), false);
    } else {
      throw new InvalidParams("Give 'text' or 'attribute'");
    }
    String type = Core.optStr(a, "type", null);
    int limit = Math.max(1, Core.optInt(a, "limit", 100));
    NodeSearch search = new NodeSearch(req);
    search.execute(under);
    JsonArray out = new JsonArray();
    int total = 0;
    Map<Document, DocumentText> texts = new HashMap<>();
    for (Pair<? extends TreeNode, List<String>> m : search.getMatches()) {
      TreeNode n = m.getFirst();
      if (n == null || n.isDisposed() || (type != null && !type.equalsIgnoreCase(n.getType())))
        continue;
      total++;
      if (out.size() >= limit)
        continue;
      JsonObject o = Core.brief(n);
      JsonArray keys = new JsonArray();
      if (m.getSecond() != null)
        m.getSecond().forEach(keys::add);
      o.add("matched", keys);
      // the node of the tree now (a search can give an instance that a load replaced)
      TreeNode current = db.getNode(n.getUUId());
      if (current instanceof Location) {
        // a location is known by its document, its text and its requirements, not by its UUID path
        Location l = (Location) current;
        Document doc = l.getDocument();
        String marked = "";
        if (doc != null) {
          o.addProperty("document", doc.getQualifiedId());
          try {
            DocumentText dt = texts.get(doc);
            if (dt == null)
              texts.put(doc, dt = DocumentText.of(doc));
            marked = dt.marked(l);
          } catch (Exception e) {
            // the document cannot be read: the text of the description
          }
        }
        // without the marked text (no ranges, or ranges outside the text): the description of the location, which the
        // core makes from the marked text
        if (marked.isEmpty() && l.getDescription() != null)
          marked = Core.text(l.getDescription()).replaceAll("\\s+", " ");
        o.addProperty("text", marked);
        o.add("requirements", strings(DocumentText.requirements(l)));
      }
      out.add(o);
    }
    JsonObject r = obj("matches", out);
    r.addProperty("total", total);
    return r;
  }

  /** The name of the document an import of the file makes, when the folder has a child of that name already; else null. */
  static String existingName(TreeNode folder, File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    String id = (dot < 0 ? name : name.substring(0, dot)) + ".xhtml";
    return folder.findWorkChild(id) != null ? id : null;
  }

  /** Imports a document file into a document folder, in the open transaction (an error makes the caller roll back). */
  static Document importInto(TreeDB db, File file, TreeNode folder) throws Exception {
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
    Map<String, String> fn = new HashMap<>();
    Map<String, Document> dc = new HashMap<>();
    id.importFilesFirst(dc, fn, files, folder, NullProgressMonitor.ONE, 1);
    Document doc = id.importFilesSecond(dc, fn, files, folder, NullProgressMonitor.ONE, 1);
    if (doc == null || !errors.isEmpty())
      throw new ToolError("Import of " + file + " failed: " + (errors.isEmpty() ? "no document was made" : errors)
          + ". Nothing was changed.");
    return doc;
  }

  /** A copy of a catalog (without its caches) in {@code <storage>/backups/<name>-<time>}. */
  static File backup(File storage, File catalog) throws IOException {
    File dir = new File(new File(storage, "backups"),
        catalog.getName() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
    Path from = catalog.toPath();
    try (Stream<Path> all = Files.walk(from)) {
      for (Path p : (Iterable<Path>) all::iterator) {
        Path rel = from.relativize(p);
        if (rel.toString().startsWith(".reqcache"))
          continue;
        Path to = dir.toPath().resolve(rel.toString());
        if (Files.isDirectory(p))
          Files.createDirectories(to);
        else
          Files.copy(p, to, StandardCopyOption.COPY_ATTRIBUTES);
      }
    }
    return dir;
  }

  /** The parameters of a template a caller can set (the root requirement is the 'root' of generate_report). */
  private static List<TemplateParamDefinition> parameters(Template t) {
    List<TemplateParamDefinition> out = new ArrayList<>();
    TemplateParamDefinition[] defs = t.getParamDescriptions();
    if (defs != null)
      for (TemplateParamDefinition d : defs)
        if (d != null && d.name != null && !d.name.equals(ReportSettings.ATTR_ROOT_REQUIREMENT))
          out.add(d);
    return out;
  }

  /** The type of a parameter: boolean, integer, number, list or string. */
  private static String paramType(TemplateParamDefinition d) {
    AttributeType t = d.types != null && d.types.length > 0 && d.types[0] != null ? d.types[0].commonType : null;
    if (t == null)
      return "string";
    switch (t) {
      case BOOL:
        return "boolean";
      case INT:
        return "integer";
      case FLOAT:
        return "number";
      case LIST:
        return "list";
      default:
        return "string";
    }
  }

  private static JsonObject generateReport(Core core, Policy policy, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    String templateId = Core.str(a, "template");
    Template tpl = TemplatesManager.getOne(db).getTemplates().get(templateId);
    if (tpl == null || tpl.getNotExists())
      throw new ToolError("No template " + templateId + " (see list_report_templates)");
    File output = policy.allowed(Core.str(a, "output"), "The output folder");
    if (output.exists() && !output.isDirectory())
      throw new ToolError(output + " is a file: give a new or empty folder");
    String[] there = output.list();
    if (there != null && there.length > 0)
      throw new ToolError("The folder " + output + " is not empty: give a new or empty folder");
    // the parameters by their definitions: a name the template does not have is an error
    Map<String, TemplateParamDefinition> defs = new HashMap<>();
    for (TemplateParamDefinition d : parameters(tpl))
      defs.put(d.name, d);
    Map<String, Object> values = new HashMap<>();
    if (a.has("parameters") && !a.get("parameters").isJsonNull()) {
      if (!a.get("parameters").isJsonObject())
        throw new InvalidParams("Argument parameters must be an object: name -> value");
      for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("parameters").entrySet()) {
        TemplateParamDefinition d = defs.get(e.getKey());
        if (d == null)
          throw new InvalidParams("Template " + templateId + " has no parameter " + e.getKey() + " (its parameters: "
              + new java.util.TreeSet<>(defs.keySet()) + ")");
        values.put(e.getKey(), paramValue(d, e.getKey(), e.getValue()));
      }
    }
    String root = Core.node(db, Core.optStr(a, "root", Requirement.getTypeRootQId())).getQualifiedId();
    TreeNode reports = Core.node(db, "/Reports");
    output.mkdirs();
    if (!output.isDirectory())
      throw new ToolError("Cannot create the folder " + output);
    // the temporary report settings are not changes of the user: not in the undo history
    ReportSettings rs = Core.write(db, "Create report settings", false, () -> {
      ReportSettings s = (ReportSettings) reports.createChild(RequalityCLI.getNextId(reports, ReportSettings.TYPE_NAME, "mcp"),
          ReportSettings.TYPE_NAME);
      s.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, tpl.getId(), 0);
      s.attrs().setConstAttribute(ReportSettings.ATTR_ROOT_REQUIREMENT, root, 0);
      s.setCrossProjectFlag(true);
      for (Map.Entry<String, Object> e : values.entrySet()) {
        Object v = e.getValue();
        if (v instanceof Boolean)
          s.attrs().setConstAttribute(e.getKey(), (Boolean) v, 0);
        else if (v instanceof Integer)
          s.attrs().setConstAttribute(e.getKey(), (Integer) v, 0);
        else if (v instanceof Double)
          s.attrs().setConstAttribute(e.getKey(), (Double) v, 0);
        else if (v instanceof ListValue)
          s.attrs().setConstAttribute(e.getKey(), (ListValue) v, 0);
        else
          s.attrs().setConstAttribute(e.getKey(), v.toString(), 0);
      }
      s.commitAttributes();
      return s;
    });
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
    // the folder was new or empty: its files are the report
    JsonArray fa = new JsonArray();
    try (Stream<Path> all = Files.walk(output.toPath())) {
      all.filter(Files::isRegularFile).sorted().forEach(p -> fa.add(p.toString()));
    }
    JsonObject r = new JsonObject();
    r.addProperty("output", output.getPath());
    r.add("files", fa);
    return r;
  }

  /** The value of a report parameter as its definition types it. */
  private static Object paramValue(TemplateParamDefinition d, String name, JsonElement v) {
    String type = paramType(d);
    try {
      switch (type) {
        case "boolean":
          if (v.isJsonPrimitive() && (v.getAsJsonPrimitive().isBoolean() || v.getAsString().matches("(?i)true|false")))
            return v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() ? v.getAsBoolean() : Boolean.parseBoolean(v.getAsString());
          break;
        case "integer":
          if (v.isJsonPrimitive())
            return Integer.parseInt(v.getAsString().trim());
          break;
        case "number":
          if (v.isJsonPrimitive())
            return Double.parseDouble(v.getAsString().trim());
          break;
        case "list": {
          List<Object> items = new ArrayList<>();
          if (v.isJsonArray())
            for (JsonElement x : v.getAsJsonArray())
              items.add(x.isJsonNull() ? "" : x.getAsString());
          else if (v.isJsonPrimitive())
            items.add(v.getAsString());
          else
            break;
          return new ListValue(AttributeType.STRING, items);
        }
        default:
          if (v.isJsonPrimitive())
            return v.getAsString();
      }
    } catch (NumberFormatException e) {
      // below
    }
    throw new InvalidParams("Parameter " + name + " must be a " + type + ", not " + v);
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
