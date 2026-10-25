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
import com.unitesk.requality.vscode.core.Nodes;
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
  /** The link of an older version of a document to its new version, and back (update_document_version). */
  static final String SUPERSEDED_BY = "superseded_by";
  static final String PREVIOUS_VERSION = "previous_version";
  private static final String NODE = "Node: its UUID or its path in the project, for example /Requirements/A/B.";
  /** get_node lists at most this many children (get_tree pages larger ones). */
  private static final int MAX_CHILDREN = 100;

  /** Adds the tools and prompts; returns the access to the core they use. */
  static Core register(McpServer mcp, HeadlessClient headless, Policy policy) {
    Core core = new Core(headless);
    Session session = new Session(core, headless.server().options().storage);
    // a catalog changed on disk loads again before a call reads it
    mcp.beforeCall((tool, args) -> core.checkDisk(mcp, args));
    // a file outside the folders of the server: the user of the client may allow its folder (elicitation)
    policy.asker((folder, reason) -> mcp.askUser("The agent asks Requality to use " + reason + ", which is outside the "
        + "folders the server may use. Allow requality-mcp to read and write files in " + folder + " until it stops?",
        "Allow " + folder, 300_000));

    // ---- orientation

    mcp.add(new Tool("list_projects", "List projects",
        "Lists the Requality projects (catalogs) in the workspace folders with their state: loading, ready, broken (with "
            + "the error), closed, locked (open in VS Code or another session: a call waits until that process gives it up), "
            + "released (unused for a while: the next call loads it again), or needs_conversion (a catalog of an older "
            + "Requality: convert_project converts it after a backup).",
        Schema.object().build(), true, false, a -> {
          JsonObject r = headless.call("workspace/projects", null).getAsJsonObject();
          JsonArray out = new JsonArray();
          for (JsonElement e : r.getAsJsonArray("projects")) {
            JsonObject p = e.getAsJsonObject();
            JsonObject o = new JsonObject();
            o.add("name", p.get("name"));
            o.add("path", p.get("path"));
            Workspace.Project wp = core.workspace().project(p.get("localName").getAsString());
            boolean old = wp != null && core.needsConversion(wp);
            String error = p.has("error") ? p.get("error").getAsString() : null;
            boolean locked = error != null && error.startsWith(Workspace.LOCKED);
            o.addProperty("state", old ? "needs_conversion" : locked ? "locked" : p.get("state").getAsString());
            if (error != null && !locked)
              o.addProperty("error", error);
            if (wp != null && wp.unlocked != null)
              o.addProperty("warning", "The catalog is not locked (" + wp.unlocked + "): the server does not change it. "
                  + "Start requality-mcp with --locks <a folder it can write>");
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
            + "With 'type' alone: the nodes of the type. Each match gives the attributes that matched; a document location "
            + "gives its text, its document and its requirements.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("text", "string", "Text to find in the user-visible attributes (case-insensitive).", false)
            .prop("attribute", "string", "Attribute name, for example status, name, id, description or type.", false)
            .prop("operator", enumSchema("Comparison for 'attribute' (default CONTAINS).", OPERATORS), false)
            .prop("value", "string", "Value for 'attribute'.", false)
            .prop("under", "string", NODE + " Search only under this node (default: the project root).", false)
            .prop("type", "string", "Only nodes of this type, for example Requirement or Location; alone: all nodes of "
                + "the type.", false)
            .prop("limit", "integer", "At most this many matches (default 100).", false).build(),
        true, false, a -> search(core, a)));

    // ---- documents to requirements

    mcp.add(new Tool("read_document", "Read document",
        "Reads a document of the project (a node under /Documents) paragraph by paragraph. Each paragraph has its "
            + "number 'p', its heading level (h1 to h6, when it is a heading), its text, its coverage by requirement "
            + "locations (none; partial, with the sentences no location covers in uncovered_text; full: each sentence is "
            + "covered) and the requirements that mark it. Use 'filter': 'uncovered' to see only the paragraphs with text "
            + "that no requirement covers yet: the work list when you turn a document into requirements with apply_changes "
            + "(op 'locate').",
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
            + "operations, changes nothing and reports every operation that fails. Operations ('op') and their keys:\n"
            + "- create: parent, type (Requirement, the default; TestPurpose, Comment, TextNode, DocFolder, ReportFolder, "
            + "VirtualNode with target, InstancesFolder, RestrictedFolder), id (default: the next free id), name, "
            + "description, attributes, template, as\n"
            + "- update: node, id, name, description, attributes (a value null removes the attribute), as\n"
            + "- update_all: under, attributes, and optionally type, where_attribute, where_value: sets (or removes) "
            + "attributes of all matching nodes of a subtree\n"
            + "- rename_attribute: under, attribute, new_name: renames an attribute in a subtree (values stay)\n"
            + "- delete: node, recursive (true to delete a node with nodes under it)\n"
            + "- move: node, parent, after (a child of the new parent; default: last)\n"
            + "- copy: node, parent, as: a copy of the node and the nodes under it (new UUIDs, the next free ids)\n"
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
            r.add("results", compact(Session.dryRun(db, label, () -> Changes.run(db, ops, true))));
            r.addProperty("dry_run", true);
            r.addProperty("note", "Nothing was changed. The operations can be applied.");
            return r;
          }
          JsonObject e = session.change(db, "apply_changes", label, summary, () -> Changes.run(db, ops));
          r.addProperty("change", e.get("change").getAsInt());
          // the values before and after are in the history (review_changes with the change)
          r.add("results", compact(e.getAsJsonArray("operations")));
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
          File file = importable(policy, Core.str(a, "file"));
          TreeNode folder = Core.node(db, Core.optStr(a, "folder", Document.getTypeRootQId()));
          String existing = existingName(folder, file);
          Document[] doc = { null };
          DocumentText[] text = { null };
          File dir = childrenFolder(db, folder);
          java.util.Set<String> before = list(dir);
          File marker = markImport(session.history(db), folder, dir, before);
          JsonObject e;
          try {
            e = session.change(db, "import_document", "Import documents", "Import of " + file.getName(), () -> {
              doc[0] = importInto(db, file, folder);
              // a document without text is no import (an unreadable or scanned file): the change is rolled back
              text[0] = DocumentText.of(doc[0]);
              if (text[0].paragraphs.isEmpty())
                throw new ToolError("The document " + file.getName() + " has no text (a scanned or protected file?). "
                    + "Nothing was changed");
              JsonObject op = Core.brief(doc[0]);
              op.addProperty("op", "import");
              op.addProperty("file", file.getPath());
              JsonArray ops = new JsonArray();
              ops.add(op);
              return ops;
            });
          } catch (Throwable x) {
            // a change that was committed (and failed after) keeps its files
            if (doc[0] == null || db.getNode(doc[0].getUUId()) == null)
              removeNew(dir, before);
            throw x;
          } finally {
            if (marker != null)
              marker.delete();
          }
          DocumentText dt = text[0];
          JsonObject r = Core.brief(doc[0]);
          r.addProperty("paragraphs", dt.paragraphs.size());
          r.addProperty("text_length", dt.text.length());
          r.addProperty("change", e.get("change").getAsInt());
          if (existing != null)
            r.addProperty("note", folder.getQualifiedId() + "/" + existing + " existed already: this import is "
                + doc[0].getQualifiedId() + ". For a new version of a document use update_document_version");
          return r;
        }));

    mcp.add(new Tool("import_reqif", "Import ReqIF",
        "Imports the requirements of one specification of a ReqIF file (.reqif or .xml, the exchange format of DOORS, "
            + "Polarion and other tools) into /Requirements of a project, in one change: the structure of the "
            + "specification, ReqIF.Text as the description, ReqIF.Name or ReqIF.ChapterName as the name, the other "
            + "attributes by their names, enumerations as enumerations of the project. Pictures and OLE objects of the "
            + "texts are left out. Import into an empty project (create_project); 'replace' imports into a project with "
            + "requirements, and the requirements that are not in the file go (revert_change brings them back).",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("file", "string", "Absolute path of the ReqIF file (in a folder of the server).", true)
            .prop("specification", "string", "The specification (its name or identifier) when the file has several.", false)
            .prop("replace", "boolean", "Import also when /Requirements has nodes (default false).", false).build(),
        false, true, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          File file = policy.file(Core.str(a, "file"), "ReqIF file", java.util.Set.of("reqif", "xml"));
          JsonObject[] result = { null };
          JsonObject e = session.change(db, "import_reqif", "Import ReqIF", "Import of " + file.getName(), () -> {
            result[0] = ReqIfImport.run(db, file, Core.optStr(a, "specification", null), Core.optBool(a, "replace", false));
            JsonObject op = result[0].deepCopy();
            op.addProperty("op", "import_reqif");
            JsonArray ops = new JsonArray();
            ops.add(op);
            return ops;
          });
          result[0].addProperty("change", e.get("change").getAsInt());
          return result[0];
        }));

    mcp.add(new Tool("update_document_version", "Update document version",
        "A new version of a specification. Call it first: its result is the list of what to fix. It imports the new "
            + "version (or takes one already imported) and moves the requirement locations of the old document to it "
            + "sentence by sentence, in one change (one undo step). The result: a summary; the sentences of the new "
            + "version that the old one does not have and no requirement marks (new_sentences; statement true when it has "
            + "shall, must or the like); and the requirements to check, lost first: lost (no similar sentence; with "
            + "suggestions), changed (moved to the most similar sentence: old and new text, similarity, words_removed and "
            + "words_added, modality_changed when shall/must/may/not changed, numbers_changed, and the new location UUID to "
            + "unlocate when it is wrong), context_changed (the same text; sentences came into or went out of its "
            + "paragraph). Unchanged requirements are counted, not listed. The old document stays as the history and links "
            + "to the new one (superseded_by); by default the requirements lose their locations in it (detach_old).",
        Schema.object().prop("project", "string", PROJECT, true).prop("document", "string", "The old document: " + NODE, true)
            .prop("file", "string", "Absolute path of the new version (in a folder of the server).", false)
            .prop("new_document", "string", "The new version, already imported: " + NODE, false)
            .prop("detach_old", "boolean", "Remove the locations of the old document from the requirements, so that they "
                + "trace to the new version only (default true; false keeps both).", false)
            .prop("include_unchanged", "boolean", "Also list the unchanged requirements (default false).", false)
            .prop("limit", "integer", "At most this many new sentences and requirements (default 50; review_changes "
                + "lists the rest).", false).build(),
        false, true, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          Document oldDoc = DocumentText.document(Core.node(db, Core.str(a, "document")));
          String path = Core.optStr(a, "file", null);
          String newRef = Core.optStr(a, "new_document", null);
          if ((path == null) == (newRef == null))
            throw new InvalidParams("Give 'file' or 'new_document' (one of them)");
          File file = path == null ? null : importable(policy, path);
          TreeNode folder = oldDoc.getWorkParent();
          // an update made already (a call repeated): the requirements trace to the newer version
          TreeNode newer = Changes.linked(oldDoc, SUPERSEDED_BY);
          if (newer != null)
            throw new ToolError(oldDoc.getQualifiedId() + " was updated to " + newer.getQualifiedId() + " already. For a newer "
                + "version update " + newer.getQualifiedId() + "; to make this update again, revert it first (review_changes "
                + "lists it)");
          Document given = newRef == null ? null : DocumentText.document(Core.node(db, newRef));
          if (given != null && given.getUUId().equals(oldDoc.getUUId()))
            throw new InvalidParams("new_document is the old document itself");
          boolean detach = Core.optBool(a, "detach_old", true);
          JsonObject[] result = { null };
          Document[] imported = { null };
          File dir = childrenFolder(db, folder);
          java.util.Set<String> before = list(dir);
          File marker = file == null ? null : markImport(session.history(db), folder, dir, before);
          JsonObject e;
          try {
            e = session.change(db, "update_document_version", "Update document version",
              "New version of " + oldDoc.getQualifiedId(), () -> {
                JsonArray ops = new JsonArray();
                Document newDoc = given;
                if (file != null) {
                  newDoc = importInto(db, file, folder);
                  imported[0] = newDoc;
                  JsonObject op = Core.brief(newDoc);
                  op.addProperty("op", "import");
                  op.addProperty("file", file.getPath());
                  ops.add(op);
                }
                result[0] = SpecUpdate.run(db, oldDoc, newDoc, detach);
                // the versions know each other: the coverage and the next update know the current one
                Changes.setLink(oldDoc, SUPERSEDED_BY, newDoc);
                Changes.setLink(newDoc, PREVIOUS_VERSION, oldDoc);
                JsonObject op = new JsonObject();
                op.addProperty("op", "update_version");
                op.addProperty("path", newDoc.getQualifiedId());
                op.addProperty("old_document", oldDoc.getQualifiedId());
                op.add("summary", result[0].get("summary"));
                // what the update did to each requirement, for the review
                op.add("requirements", result[0].get("requirements"));
                op.add("new_sentences", result[0].get("new_sentences"));
                ops.add(op);
                return ops;
              });
          } catch (Throwable x) {
            // a change that was committed (and failed after) keeps its files
            if (file != null && (imported[0] == null || db.getNode(imported[0].getUUId()) == null))
              removeNew(dir, before);
            throw x;
          } finally {
            if (marker != null)
              marker.delete();
          }
          int change = e.get("change").getAsInt();
          JsonObject r = SpecUpdate.brief(result[0], Core.optBool(a, "include_unchanged", false),
              Math.max(1, Core.optInt(a, "limit", 50)), change);
          r.addProperty("change", change);
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
              Core.optBool(a, "refresh", false), Core.optBool(a, "documents", true), Math.max(1, Core.optInt(a, "limit", 50)));
        }).alone(a -> a.has("refresh") && Core.optBool(a, "refresh", false)));

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
            + "first nodes and state (done, undone or reverted). With project and change: the operations of that change "
            + "with the values before and after, a page at a time (offset, limit; next_offset when there are more).",
        Schema.object().prop("project", "string", "Only the changes of this project.", false)
            .prop("change", "integer", "One change (with project): its operations.", false)
            .prop("offset", "integer", "With change: the first operation (or list item of a document update) to give "
                + "(default 0).", false)
            .prop("limit", "integer", "With change: at most this many operations or list items (default 100).", false)
            .prop("verbose", "boolean", "The operations of each change (default false).", false)
            .prop("last", "integer", "At most this many changes (default 20).", false).build(),
        true, false, a -> session.review(Core.optStr(a, "project", null), Math.max(1, Core.optInt(a, "last", 20)),
            a.has("change") && !a.get("change").isJsonNull() ? Core.optInt(a, "change", 0) : null, Core.optBool(a, "verbose", false),
            Math.max(0, Core.optInt(a, "offset", 0)), Math.max(1, Core.optInt(a, "limit", 100)))));

    mcp.add(new Tool("compare_with_revision", "Compare with revision",
        "The nodes of a project that differ from a Git revision of its catalog (the catalog must be in a Git repository): "
            + "added, deleted and changed nodes with the attributes that changed (name, description, locations, user "
            + "attributes; before and after) and the old path of a moved or renamed node. It shows every change since the "
            + "revision, also the ones of VS Code, of a colleague or of another session: for a review before a commit or a "
            + "pull request.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("revision", "string", "A commit, branch or tag, for example HEAD (default), main or HEAD~3.", false)
            .prop("under", "string", "Only the nodes under this path, for example /Requirements/A.", false)
            .prop("limit", "integer", "At most this many nodes in each list (default 100).", false).build(),
        true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          Workspace.Project p = core.workspace().project(db.getLocalName());
          return RevisionDiff.compare(p.dir, Core.optStr(a, "revision", "HEAD"), Core.optStr(a, "under", null),
              Math.max(1, Core.optInt(a, "limit", 100)));
        }));

    mcp.add(new Tool("revert_change", "Revert change",
        "Reverts a change of review_changes, also of an earlier session: its nodes become as they were before it, with "
            + "their UUIDs (deleted nodes come back with their links and locations), in one change. It is refused when a "
            + "node of the change was changed later (by the agent or in VS Code), or when other nodes link to a node the "
            + "revert deletes; the error lists them and the later changes to revert first. 'force' reverts anyway (the "
            + "later changes of these nodes are lost, such links break); 'dry_run' tells what would be reverted. The result "
            + "counts the nodes restored, recreated and deleted, with the first ones.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("change", "integer", "The change number (see review_changes).", true)
            .prop("dry_run", "boolean", "Tell what the revert does, change nothing (default false).", false)
            .prop("force", "boolean", "Revert also when nodes of the change were changed later (default false).", false)
            .prop("verbose", "boolean", "List every node of the revert (default: the first " + Session.SHOWN + ").", false)
            .build(),
        false, true, a -> session.revert(core.db(Core.str(a, "project")), Core.optInt(a, "change", 0),
            Core.optBool(a, "force", false), Core.optBool(a, "dry_run", false), Core.optBool(a, "verbose", false))));

    mcp.add(new Tool("undo", "Undo",
        "Undoes the newest change of the agent in this session (in any project) and marks it undone in review_changes. "
            + "For a change of an earlier session use revert_change.",
        Schema.object().build(), false, true, a -> session.undoRedo(true)));
    mcp.add(new Tool("redo", "Redo", "Redoes the newest undone change of this session.", Schema.object().build(), false, true,
        a -> session.undoRedo(false)));

    // ---- project settings

    mcp.add(new Tool("get_project_settings", "Get project settings",
        "The settings of a project: the project attributes (every node inherits them), the enumerations (enum types), "
            + "the mandatory attributes of each node type with their defaults, the checker rules (JavaScript formulas, "
            + "true for a correct node), the checkers of user-visible ids and defined terms, the id prefix, the default "
            + "link attribute and the node templates of /Templates.",
        Schema.object().prop("project", "string", PROJECT, true).build(), true, false,
        a -> ProjectSettings.read(core.db(Core.str(a, "project")))));

    mcp.add(new Tool("change_project_settings", "Change project settings",
        "Changes the settings of a project in one change (one undo step, revertible). Give only what changes:\n"
            + "- attributes: name -> {\"type\": string|integer|number|boolean|list|html|enum:<enumeration>, \"value\": ...} "
            + "or null (removes it): project attributes, inherited by every node\n"
            + "- enums: name -> {\"type\": string|integer|number, \"values\": [value or {\"value\", \"comment\"}]} or null "
            + "(removes an unused one); a value that nodes use cannot be removed\n"
            + "- required_attributes: node type -> [{\"attribute\", \"default\"}] or null: the nodes of the type get the "
            + "attributes they miss, with the default\n"
            + "- checker_rules: [{\"name\", \"formula\"}] replaces the rules (JavaScript, true for a correct node; the "
            + "formulas see attribute values, not files or programs)\n"
            + "- checkers: {\"user_visible_ids\": boolean, \"defined_terms\": boolean}\n"
            + "- id_prefix, default_link_attribute: strings\n"
            + "- active_templates: node type -> template id of /Templates, or null",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("attributes", "object", "Project attributes to set (or null to remove).", false)
            .prop("enums", "object", "Enumerations to set (or null to remove).", false)
            .prop("required_attributes", "object", "Mandatory attributes by node type.", false)
            .prop("checker_rules", rulesSchema(), false)
            .prop("checkers", "object", "Checkers to switch on or off.", false)
            .prop("id_prefix", "string", "The id prefix of the project (empty: the default).", false)
            .prop("default_link_attribute", "string", "The attribute of new links.", false)
            .prop("active_templates", "object", "Active node template by node type (or null).", false)
            .prop("summary", "string", "What the change does and why, for the person who reviews it.", false).build(),
        false, true, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          JsonObject e = session.change(db, "change_project_settings", "Change project settings",
              Core.optStr(a, "summary", null), () -> ProjectSettings.change(db, a));
          JsonObject r = new JsonObject();
          r.addProperty("change", e.get("change").getAsInt());
          r.add("results", compact(e.getAsJsonArray("operations")));
          return r;
        }));

    // ---- reports and projects

    mcp.add(new Tool("list_report_templates", "List report templates",
        "The report templates the project can use: id, name (in English; name_original is the name in Requality), "
            + "purpose, group, description and parameters (name, type, description, default).",
        Schema.object().prop("project", "string", PROJECT, true).build(), true, false, a -> {
          TreeDB db = core.db(Core.str(a, "project"));
          JsonArray out = new JsonArray();
          for (Template t : TemplatesManager.getOne(db).getTemplates().values()) {
            if (t.getNotExists())
              continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", t.getId());
            String[] en = TEMPLATES.get(t.getId());
            o.addProperty("name", en != null ? en[0] : t.getName());
            if (en != null && !en[0].equals(t.getName())) {
              o.addProperty("name_original", t.getName());
              o.addProperty("purpose", en[1]);
            }
            o.addProperty("group", t.getGroup());
            String description = Core.text(t.getDescription());
            if (!description.isEmpty())
              o.addProperty("description", description);
            JsonArray ps = new JsonArray();
            for (TemplateParamDefinition d : parameters(t)) {
              JsonObject p = new JsonObject();
              p.addProperty("name", d.name);
              p.addProperty("type", paramType(d));
              p.addProperty("description", PARAMETERS.getOrDefault(d.name,
                  d.description == null ? "" : d.description.replaceAll("\\s*//\\s*TODO.*$", "").trim()));
              Object def = d.defvalue instanceof com.unitesk.requality.core.attribute.TypedValue
                  ? ((com.unitesk.requality.core.attribute.TypedValue) d.defvalue).getValue() : d.defvalue;
              if (def != null)
                p.addProperty("default", def.toString());
              ps.add(p);
            }
            o.add("parameters", ps);
            out.add(o);
          }
          return obj("templates", out);
        }));

    mcp.add(new Tool("generate_report", "Generate report",
        "Generates a report from a template (see list_report_templates) for the requirements under a node, or from saved "
            + "report settings of the project (a node under /Reports), and writes it to a new or empty folder in a folder "
            + "of the server (not inside a catalog), for example to show it to a person. The coverage report takes the test "
            + "coverage from coverage_file (the file of test_coverage). 'save_as' also saves the template, root and "
            + "parameters as report settings under /Reports (one change), for later reports here or in VS Code.",
        Schema.object().prop("project", "string", PROJECT, true)
            .prop("template", "string", "Template id (or give settings).", false)
            .prop("settings", "string", "Saved report settings: " + NODE + " (instead of template, root and parameters).", false)
            .prop("output", "string", "Absolute path of the output folder: new or empty.", true)
            .prop("root", "string", "Requirements under this node (default /Requirements).", false)
            .prop("coverage_file", "string", "Absolute path of a coverage file of the test runs, for the coverage report.", false)
            .prop("parameters", paramsSchema(), false)
            .prop("save_as", "string", "With template: save the settings as a node with this id under /Reports.", false).build(),
        false, false, a -> generateReport(core, session, policy, a)));

    mcp.add(new Tool("open_project", "Open project",
        "Opens a closed project, loads a broken one again (for example after another process released its catalog), or "
            + "loads a ready one again from its files (for example after git pull changed them), and waits until it is "
            + "loaded. The undo steps of the project end with a load.",
        Schema.object().prop("project", "string", PROJECT, true).build(), false, false, a -> {
          String name = Core.str(a, "project");
          Workspace.Project p = core.project(name);
          String done;
          if (p.closed) {
            core.workspace().openProject(p.dir);
            done = "opened";
          } else if (p.error != null || com.unitesk.requality.vscode.core.Nodes.findDB(p.localName) == null) {
            core.workspace().loadProject(p.dir);
            done = "loaded";
          } else {
            core.workspace().reloadProject(p.dir);
            done = "loaded again";
          }
          core.db(name);
          JsonObject r = obj("project", new JsonPrimitive(name));
          r.addProperty("state", done);
          return r;
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
          // the question of the conversion comes while the project loads: the load ends first
          long loading = System.currentTimeMillis() + Core.LOAD_WAIT_MS;
          while (!core.needsConversion(p) && !p.closed && p.error == null && System.currentTimeMillis() < loading) {
            TreeDB loaded = Nodes.findDB(p.localName);
            if (!p.loading && loaded != null && Nodes.isReady(loaded))
              break;
            Core.sleep(200);
          }
          if (!core.needsConversion(p))
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
    return core;
  }

  static final String[] OPERATORS = { "EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "EXISTS", "NOT_EXISTS" };
  static final String[] FILTERS = { "all", "uncovered", "partial", "covered" };

  // ---- helpers

  /** The results of the operations without the values before and after (they are in the history). */
  static JsonArray compact(JsonArray results) {
    JsonArray out = new JsonArray();
    for (JsonElement x : results) {
      JsonObject o = x.getAsJsonObject().deepCopy();
      o.remove("before");
      o.remove("after");
      out.add(o);
    }
    return out;
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
    s.addProperty("description", "Template parameters: name -> value (see the parameters of list_report_templates; a "
        + "boolean, a number, a string, or a list of strings).");
    return s;
  }

  /** One schema for each operation (a JSON Schema "oneOf" on 'op'), with the keys the operation takes. */
  /** The schema of the checker rules of change_project_settings. */
  static JsonObject rulesSchema() {
    JsonObject item = Schema.object().prop("name", "string", "The name of the rule (shown with a problem).", true)
        .prop("formula", "string", "JavaScript that is true for a correct node.", true).build();
    JsonObject s = new JsonObject();
    s.addProperty("type", "array");
    s.addProperty("description", "All checker rules (they replace the ones of the project).");
    s.add("items", item);
    return s;
  }

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
            break;
          case "recursive":
            s.addProperty("type", "boolean");
            break;
          default:
            s.addProperty("type", "string");
        }
        s.addProperty("description", Changes.MEANING.get(k));
        p.add(k, s);
      }
      item.add("properties", p);
      JsonArray req = new JsonArray();
      req.add("op");
      for (String k : Changes.REQUIRED.get(e.getKey()))
        req.add(k);
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
    JsonArray broken = Core.brokenLinks(n);
    if (broken.size() > 0)
      o.add("broken_links", broken);
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

  static JsonArray strings(List<String> l) {
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
      boolean match = filter.equals("all") || (filter.equals("uncovered") && !c.equals("full"))
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
    // the versions of the document (update_document_version links them)
    TreeNode newer = Changes.linked(dt.doc, SUPERSEDED_BY);
    if (newer != null)
      r.addProperty("superseded_by", newer.getQualifiedId());
    TreeNode older = Changes.linked(dt.doc, PREVIOUS_VERSION);
    if (older != null)
      r.addProperty("previous_version", older.getQualifiedId());
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

  /**
   * The key of an attribute by the name an agent gives: a system attribute by its name in the editor in any case (name,
   * %Name, Description), else a user attribute that a node of the project has; null when no node has it.
   */
  static String attributeKey(TreeDB db, String name) {
    com.unitesk.requality.core.attribute.SystemNames sn = com.unitesk.requality.core.attribute.SystemNames.getOne(db);
    String sys = sn.getSysName(name);
    if (sys != null)
      return sys;
    String bare = name.replaceFirst("^[%_]+", "");
    for (String v : sn.getVisSysNames())
      if (v.replaceFirst("^%", "").equalsIgnoreCase(bare)) {
        sys = sn.getSysName(v);
        if (sys != null)
          return sys;
      }
    if (bare.equalsIgnoreCase("nameorid"))
      return TreeNode.ATTR_NAMEORID;
    // a user attribute (or another system one, by its key) that some node has
    NodeSearch exists = new NodeSearch(new AttributeRequest(name, AttributeOperator.EXISTS, "", false));
    exists.execute(db.getRootNode());
    return exists.getMatches().isEmpty() ? null : name;
  }

  /** The user attribute names of the nodes of a subtree (at most {@code max} nodes looked at), sorted. */
  static java.util.Set<String> userAttributes(TreeNode root, int max) {
    java.util.Set<String> out = new java.util.TreeSet<>();
    java.util.ArrayDeque<TreeNode> q = new java.util.ArrayDeque<>();
    q.add(root);
    int seen = 0;
    while (!q.isEmpty() && seen++ < max) {
      TreeNode n = q.poll();
      for (com.unitesk.requality.core.attribute.Attribute x : n.attrs())
        if (!x.getKey().startsWith("_") && !x.getKey().startsWith("%"))
          out.add(x.getKey());
      for (TreeNode c : n.getWorkChildren())
        if (c != null && !c.isDisposed() && !(c instanceof Location))
          q.add(c);
    }
    return out;
  }

  private static JsonObject search(Core core, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    TreeNode under = Core.node(db, Core.optStr(a, "under", "/"));
    String text = Core.optStr(a, "text", null);
    String attribute = Core.optStr(a, "attribute", null);
    AttributeRequest req;
    String type = Core.optStr(a, "type", null);
    if (text != null) {
      req = new AttributeRequest(AttributeRequest.USER_VISIBLE_KEY, AttributeOperator.CONTAINS_SINGLE_SPACE, text, false);
    } else if (attribute != null) {
      AttributeOperator op = AttributeOperator.valueOf(Core.optEnum(a, "operator", "CONTAINS", OPERATORS));
      String key = attributeKey(db, attribute);
      if (key == null)
        throw new ToolError("No node of project " + db.getSemiProjectName() + " has the attribute " + attribute
            + ". Attributes: name, id, description, type, " + userAttributes(db.getRootNode(), 5000));
      req = new AttributeRequest(key, op, Core.optStr(a, "value", ""), false);
    } else if (type != null) {
      // all nodes of the type: every node has a type
      req = new AttributeRequest(TreeNode.ATTR_TYPE, AttributeOperator.EXISTS, "", false);
    } else {
      throw new InvalidParams("Give 'text', 'attribute' or 'type'");
    }
    int limit = Math.max(1, Core.optInt(a, "limit", 100));
    NodeSearch search = new NodeSearch(req);
    search.execute(under);
    JsonArray out = new JsonArray();
    int total = 0;
    Map<Document, DocumentText> texts = new HashMap<>();
    for (Pair<? extends TreeNode, List<String>> m : search.getMatches()) {
      TreeNode n = m.getFirst();
      // a virtual node is a copy of its target: the target is the match
      if (n == null || n.isDisposed() || n.isVirtual() || (type != null && !type.equalsIgnoreCase(n.getType())))
        continue;
      total++;
      if (out.size() >= limit)
        continue;
      JsonObject o = Core.brief(n);
      JsonArray keys = new JsonArray();
      if (m.getSecond() != null)
        for (String k : m.getSecond()) {
          // the system attributes by their names in the editor (_name, _nameorid: name)
          String shown = k.equals("_nameorid") || k.equals("%Name") ? "name" : k.replaceFirst("^[_%]+", "");
          if (!keys.contains(new JsonPrimitive(shown)))
            keys.add(shown);
        }
      if (text != null || attribute != null)
        o.add("matched", keys);
      // the node of the tree now (a search can give an instance that a load replaced)
      TreeNode current = db.getNode(n.getUUId());
      if (current instanceof Location) {
        // a location is known by its document, its text and its requirements, not by its UUID path
        o.remove("path");
        o.remove("id");
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

  /** The folder on disk of the children of a node (the importer writes the files of a new document there). */
  static File childrenFolder(TreeDB db, TreeNode node) {
    try {
      File json = db.getStorage().getJSONResource(node.getUUId(), com.unitesk.requality.core.lock.LockType.NO_LOCK);
      if (json == null)
        return null;
      String n = json.getName();
      return new File(json.getParentFile(), n.endsWith(".json") ? n.substring(0, n.length() - 5) : n);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Records an import that starts (the folder of the files of the document folder and its files now) in the history
   * folder of the catalog: when the server stops during the import, the next one removes the files it left
   * ({@link Core#recoverImport}). Null when there is no history folder.
   */
  static File markImport(History h, TreeNode folder, File dir, java.util.Set<String> before) {
    if (h.dir() == null || dir == null)
      return null;
    JsonObject m = new JsonObject();
    m.addProperty("folder", folder.getSUUId());
    m.addProperty("dir", dir.getAbsolutePath());
    JsonArray b = new JsonArray();
    before.forEach(b::add);
    m.add("before", b);
    File f = new File(h.dir(), IMPORT_MARKER);
    try {
      Files.writeString(f.toPath(), m.toString());
    } catch (IOException x) {
      throw new ToolError("The import cannot start: " + x.getMessage());
    }
    return f;
  }

  static final String IMPORT_MARKER = "import.json";

  static java.util.Set<String> list(File dir) {
    String[] l = dir == null ? null : dir.list();
    return l == null ? new java.util.HashSet<>() : new java.util.HashSet<>(Arrays.asList(l));
  }

  /** Removes the files a failed (rolled back) import left in a folder: the ones that were not there before. */
  static void removeNew(File dir, java.util.Set<String> before) {
    String[] now = dir == null ? null : dir.list();
    if (now == null)
      return;
    for (String n : now)
      if (!before.contains(n))
        deleteTree(new File(dir, n));
  }

  static void deleteTree(File f) {
    File[] l = f.listFiles();
    if (l != null)
      for (File c : l)
        deleteTree(c);
    f.delete();
  }

  /** The name of the document an import of the file makes, when the folder has a child of that name already; else null. */
  static String existingName(TreeNode folder, File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    String id = (dot < 0 ? name : name.substring(0, dot)) + ".xhtml";
    return folder.findWorkChild(id) != null ? id : null;
  }

  /** The document file to import: in an allowed folder, of a document type, with no references the import must not follow. */
  static File importable(Policy policy, String path) throws IOException {
    File file = policy.document(path);
    List<String> bad = policy.unsafeReferences(file);
    if (!bad.isEmpty())
      throw new ToolError("The document " + file.getName() + " refers to URLs or to files outside the folders of the server: "
          + (bad.size() > 10 ? bad.subList(0, 10) + " and " + (bad.size() - 10) + " more" : bad) + ". The import would copy "
          + "or download them into the catalog. Remove these references (or put the files next to the document) and import "
          + "again");
    return file;
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
    // the text of a PDF has no headings: numbered headings and the titles of its outline become headings
    if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
      PdfHeadings.recover(doc);
    return doc;
  }

  /** The backups of a catalog the storage keeps: the newest ones. */
  static final int BACKUPS = 3;

  /** A copy of a catalog (without its caches) in {@code <storage>/backups/<name>-<time>}; older copies go. */
  static File backup(File storage, File catalog) throws IOException {
    File backups = new File(storage, "backups");
    File dir = new File(backups,
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
    String[] all = backups.list();
    if (all != null) {
      List<String> mine = new ArrayList<>();
      for (String n : all)
        if (n.matches(java.util.regex.Pattern.quote(catalog.getName()) + "-\\d{8}-\\d{6}"))
          mine.add(n);
      java.util.Collections.sort(mine);
      for (int i = 0; i < mine.size() - BACKUPS; i++)
        deleteTree(new File(backups, mine.get(i)));
    }
    return dir;
  }

  /** The parameters of a template a caller can set (the root requirement is the 'root' of generate_report). */
  /** The templates of Requality (their names are in Russian): the English name and what the report is for. */
  private static final Map<String, String[]> TEMPLATES = new HashMap<>();
  static {
    String p = "com.unitesk.requality.reports.";
    TEMPLATES.put(p + "coverage", new String[] { "Requirement coverage analysis",
        "The requirements and the tests that cover them (give coverage_file, the file of test_coverage)." });
    TEMPLATES.put(p + "dm", new String[] { "Coverage analysis of the marked requirements",
        "The documents with the marked text of each requirement and the coverage of the requirements by tests." });
    TEMPLATES.put(p + "em", new String[] { "Requirement markup analysis",
        "The documents with the text each requirement marks: a person checks the markup without Requality." });
    TEMPLATES.put(p + "violations", new String[] { "Formatting rule check",
        "The problems that the checkers and the checker rules of the project find in the requirements." });
    TEMPLATES.put(p + "checkerserrors", new String[] { "Formatting rule debugging",
        "The results of each checker rule for each requirement, for the author of the rules." });
    TEMPLATES.put(p + "reqSpecification", new String[] { "Export to LORequality",
        "A requirement specification document (for LibreOffice with the LORequality extension)." });
    TEMPLATES.put(p + "statistic", new String[] { "Project progress",
        "Statistics of the project over time: requirements, coverage and changes by date." });
    TEMPLATES.put(p + "tableview", new String[] { "Reading requirements",
        "The requirements as a table with the attributes you choose (selAttrs)." });
    TEMPLATES.put(p + "trace", new String[] { "Two-way link view",
        "The links between the requirements in both directions (filter selects the link attributes)." });
    TEMPLATES.put(p + "traceability", new String[] { "Configurable traceability matrix",
        "A matrix of the links between the requirements, with the columns of traceabilitySettings." });
    TEMPLATES.put(p + "xml-out", new String[] { "Project export to XML", "The nodes and attributes of the project as XML." });
  }

  /** Descriptions of the parameters whose descriptions in the templates are not finished. */
  private static final Map<String, String> PARAMETERS = new HashMap<>();
  static {
    PARAMETERS.put("attrFilter", "The attributes the report shows for each requirement: attribute names (any case), or "
        + "*ref for all link attributes, *ref<name> for one, *ref<prefix>* or *ref*<suffix> for some, and *ref!... for the "
        + "link attributes that do not match.");
    PARAMETERS.put("traceabilitySettings", "The columns of the matrix: a list of lists of strings. An optional first item "
        + "of two strings gives the templates of the main and the additional text. Each other item is a column: the text "
        + "of a cell, its link ({this-page} or {next-page} with #{value}, or empty), the text when the target is missing, "
        + "and the text without a link. Placeholders: {user-visible-name}, {id}, {name-or-id}, {attribute-name}, {value}; "
        + "{skip-line} leaves the line out.");
  }

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

  private static JsonObject generateReport(Core core, Session session, Policy policy, JsonObject a) throws Exception {
    TreeDB db = core.db(Core.str(a, "project"));
    String templateId = Core.optStr(a, "template", null);
    String settingsRef = Core.optStr(a, "settings", null);
    String saveAs = Core.optStr(a, "save_as", null);
    if ((templateId == null) == (settingsRef == null))
      throw new InvalidParams("Give 'template' or 'settings' (one of them)");
    if (saveAs != null && templateId == null)
      throw new InvalidParams("Argument save_as is for a template (the settings are saved already)");
    ReportSettings saved = null;
    Template tpl = null;
    if (settingsRef != null) {
      TreeNode n = Core.node(db, settingsRef);
      if (!(n instanceof ReportSettings))
        throw new ToolError(n.getQualifiedId() + " is no report settings (a " + n.getType() + ")");
      for (String k : new String[] { "root", "parameters" })
        if (a.has(k) && !a.get(k).isJsonNull())
          throw new InvalidParams("Argument " + k + " is for a template; saved settings have their own");
      saved = (ReportSettings) n;
      String tid = saved.attrs().getStringAttributeValue(ReportSettings.ATTR_TEMPLATE);
      Template st = tid == null ? null : TemplatesManager.getOne(db).getTemplates().get(tid);
      if (st == null || st.getNotExists())
        throw new ToolError("The settings " + n.getQualifiedId() + " use the template " + tid + ", which is not installed "
            + "here (see list_report_templates)");
    } else {
      tpl = TemplatesManager.getOne(db).getTemplates().get(templateId);
      if (tpl == null || tpl.getNotExists())
        throw new ToolError("No template " + templateId + " (see list_report_templates)");
    }
    File output = policy.allowed(Core.str(a, "output"), "The output folder");
    // a report in a catalog would put files (also templates of .templates) where Requality reads them
    for (Workspace.Project p : core.workspace().projects())
      if (p.requality && Policy.inside(output, p.dir))
        throw new ToolError("The output folder " + output + " is inside the catalog " + p.dir + ": give a folder outside "
            + "the catalogs");
    if (output.exists() && !output.isDirectory())
      throw new ToolError(output + " is a file: give a new or empty folder");
    String[] there = output.list();
    if (there != null && there.length > 0)
      throw new ToolError("The folder " + output + " is not empty: give a new or empty folder");
    String coverage = null;
    if (a.has("coverage_file") && !a.get("coverage_file").isJsonNull())
      coverage = policy.file(Core.str(a, "coverage_file"), "coverage file", java.util.Set.of("xml")).toURI().toString();
    if (saved != null)
      checkCoverage(policy, db, saved, coverage != null);
    // the parameters by their definitions: a name the template does not have is an error
    Map<String, Object> values = new HashMap<>();
    if (tpl != null) {
      Map<String, TemplateParamDefinition> defs = new HashMap<>();
      for (TemplateParamDefinition d : parameters(tpl))
        defs.put(d.name, d);
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
    }
    String root = tpl == null ? null : Core.node(db, Core.optStr(a, "root", Requirement.getTypeRootQId())).getQualifiedId();
    TreeNode reports = Core.node(db, "/Reports");
    JsonObject out = new JsonObject();
    if (saveAs != null) {
      // the saved settings are a change of the agent; the report is made from them
      if (reports.findWorkChild(saveAs) != null)
        throw new ToolError("/Reports/" + saveAs + " exists already: give another save_as");
      try {
        db.getTreeLogic().isIdValidExc(reports, saveAs);
      } catch (Exception e) {
        throw new ToolError("Invalid save_as: " + Core.message(e));
      }
      Template t = tpl;
      ReportSettings[] made = { null };
      JsonObject e = session.change(db, "generate_report", "Save report settings", "Report settings " + saveAs, () -> {
        made[0] = settings(reports, saveAs, t, root, values, null);
        JsonObject op = Core.brief(made[0]);
        op.addProperty("op", "create");
        op.addProperty("template", t.getId());
        JsonArray ops = new JsonArray();
        ops.add(op);
        return ops;
      });
      saved = made[0];
      tpl = null;
      out.addProperty("settings", saved.getQualifiedId());
      out.addProperty("change", e.get("change").getAsInt());
    }
    output.mkdirs();
    if (!output.isDirectory())
      throw new ToolError("Cannot create the folder " + output);
    Template t = tpl;
    String cov = coverage;
    ReportSettings savedSettings = saved;
    // the temporary report settings (or the coverage file of saved ones) are not changes of the user: not in the undo
    // history
    String[] savedCoverage = { null };
    ReportSettings rs = Core.write(db, "Create report settings", false, () -> {
      if (savedSettings != null) {
        if (cov != null) {
          savedCoverage[0] = savedSettings.attrs().getStringAttributeValue("coverageFilePath");
          savedSettings.attrs().setConstAttribute("coverageFilePath", cov, 0);
          savedSettings.commitAttributes();
        }
        return savedSettings;
      }
      return settings(reports, RequalityCLI.getNextId(reports, ReportSettings.TYPE_NAME, "mcp"), t, root, values, cov);
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
        if (savedSettings == null) {
          if (!rs.isDisposed())
            rs.delete();
        } else if (cov != null) {
          if (savedCoverage[0] != null)
            savedSettings.attrs().setConstAttribute("coverageFilePath", savedCoverage[0], 0);
          else
            savedSettings.attrs().removeAttribute("coverageFilePath");
          savedSettings.commitAttributes();
        }
        return null;
      });
    }
    // the folder was new or empty: its files are the report
    JsonArray fa = new JsonArray();
    try (Stream<Path> all = Files.walk(output.toPath())) {
      all.filter(Files::isRegularFile).sorted().forEach(p -> fa.add(p.toString()));
    }
    out.addProperty("output", output.getPath());
    out.add("files", fa);
    return out;
  }

  /** New report settings (in the open transaction): the template, the root, the parameters and the coverage file. */
  private static ReportSettings settings(TreeNode reports, String id, Template t, String root, Map<String, Object> values,
      String coverage) {
    ReportSettings s = (ReportSettings) reports.createChild(id, ReportSettings.TYPE_NAME, TreeDB.EMPTY_TEMPLATE);
    s.attrs().setConstAttribute(ReportSettings.ATTR_ROOT_REQUIREMENT, root, 0);
    s.setCrossProjectFlag(true);
    if (coverage != null)
      s.attrs().setConstAttribute("coverageFilePath", coverage, 0);
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
    // the template, as the report settings of Requality keep it (hidden, with its name and output file)
    TemplatesManager.getOne(reports.getTreeDB()).saveReportSettings(s, t, null);
    s.commitAttributes();
    return s;
  }

  /** The attributes of report settings that name where the coverage of the tests comes from. */
  static final java.util.Set<String> COVERAGE_ATTRIBUTES = java.util.Set.of("coverageFilePath", "coverageStorageType",
      "coverageStorageSettings");

  /**
   * The coverage of saved report settings comes from a file in a folder of the server (the report reads it): not from a
   * URL, another file or another source. {@code given}: a coverage_file replaces the file of the settings.
   */
  private static void checkCoverage(Policy policy, TreeDB db, ReportSettings s, boolean given) {
    String type = s.attrs().getStringAttributeValue("coverageStorageType");
    if (type != null && !type.isEmpty()
        && !type.equals(com.unitesk.requality.core.api.IAPICoverageSource.COVERAGE_FILE_ID))
      throw new ToolError("The settings " + s.getQualifiedId() + " take the coverage from the source " + type + ", which this "
          + "server does not read");
    List<String> paths = new ArrayList<>();
    if (!given) {
      String path = s.attrs().getStringAttributeValue("coverageFilePath");
      if (path != null && !path.isEmpty() && (type == null || type.isEmpty()))
        paths.add(path);
    }
    String setts = s.attrs().getStringAttributeValue("coverageStorageSettings");
    if (type != null && !type.isEmpty() && setts != null)
      for (String x : setts.split(","))
        if (!x.isBlank())
          paths.add(x.trim());
    for (String path : paths) {
      String p = path.startsWith(".") ? RequalityCLI.getSemiAbsolutePath(path, db) : path;
      File f;
      try {
        java.net.URL url = new java.net.URL(p);
        if (!"file".equalsIgnoreCase(url.getProtocol()))
          throw new java.net.MalformedURLException(url.getProtocol());
        f = new File(url.toURI());
      } catch (java.net.MalformedURLException | java.net.URISyntaxException | IllegalArgumentException e) {
        throw new ToolError("The settings " + s.getQualifiedId() + " take the coverage from " + path + ", which is no file: "
            + "give coverage_file");
      }
      try {
        policy.file(f.getPath(), "coverage file", java.util.Set.of("xml"));
      } catch (ToolError e) {
        throw new ToolError("The settings " + s.getQualifiedId() + " take the coverage from " + path + ": "
            + e.getMessage() + ". Give coverage_file");
      }
    }
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
