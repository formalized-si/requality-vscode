package com.unitesk.requality.vscode.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.NullProgressMonitor;
import com.unitesk.requality.core.operation.document.ProcessNewSpecificationVersion;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * update_document_version: the locations of a document move to its new version (the "Update Document" processor of
 * Requality, ProcessNewSpecificationVersion), and the result tells for each requirement what happened to its marked
 * text: transferred unchanged, transferred with changed text, or lost. The processor also creates review items
 * (ToDoItem nodes under /ToDoLinks) for the requirements to check.
 */
final class SpecUpdate {
  private SpecUpdate() {
  }

  /** The marked texts of the locations of a document, for each requirement. */
  private static Map<String, List<String>> marks(DocumentText dt) {
    Map<String, List<String>> m = new LinkedHashMap<>();
    for (Location l : dt.locations)
      for (String r : DocumentText.requirements(l))
        m.computeIfAbsent(r, k -> new ArrayList<>()).add(dt.marked(l));
    return m;
  }

  static JsonObject run(TreeDB db, Document oldDoc, Document newDoc) throws Exception {
    if (oldDoc == newDoc)
      throw new ToolError("The old and the new document are the same node");
    if (newDoc.attrs().getAttribute(Document.ATTR_ALREADY_UPDATED) != null)
      throw new ToolError(newDoc.getQualifiedId() + " was already updated; a document is updated once");
    Map<String, List<String>> before = marks(DocumentText.of(oldDoc));
    ProcessNewSpecificationVersion p = new ProcessNewSpecificationVersion();
    p.setShowMessages(false, true);
    if (!p.configure(newDoc, oldDoc, true))
      throw new ToolError("The update of " + newDoc.getQualifiedId() + " from " + oldDoc.getQualifiedId() + " cannot start");
    ByteArrayOutputStream log = new ByteArrayOutputStream();
    try (PrintStream ps = new PrintStream(log, true, StandardCharsets.UTF_8)) {
      // the processor runs its own transaction of the tree (one undo step)
      p.process(newDoc, db.getNode(Requirement.getTypeRootQId()), ps, null, NullProgressMonitor.ONE);
    }
    Map<String, List<String>> after = marks(DocumentText.of(newDoc));
    JsonArray reqs = new JsonArray();
    int same = 0;
    int changed = 0;
    int lost = 0;
    for (Map.Entry<String, List<String>> e : before.entrySet()) {
      List<String> now = after.get(e.getKey());
      JsonObject r = new JsonObject();
      r.addProperty("requirement", e.getKey());
      String status;
      if (now == null || now.isEmpty()) {
        status = "lost";
        lost++;
      } else if (new java.util.HashSet<>(now).equals(new java.util.HashSet<>(e.getValue()))) {
        status = "unchanged";
        same++;
      } else {
        status = "changed";
        changed++;
      }
      r.addProperty("status", status);
      if (!"unchanged".equals(status)) {
        JsonArray b = new JsonArray();
        e.getValue().forEach(b::add);
        r.add("old_text", b);
        if (now != null) {
          JsonArray a = new JsonArray();
          now.forEach(a::add);
          r.add("new_text", a);
        }
      }
      reqs.add(r);
    }
    JsonObject o = new JsonObject();
    o.addProperty("old_document", oldDoc.getQualifiedId());
    o.addProperty("new_document", newDoc.getQualifiedId());
    JsonObject stats = new JsonObject();
    stats.addProperty("requirements", before.size());
    stats.addProperty("unchanged", same);
    stats.addProperty("changed", changed);
    stats.addProperty("lost", lost);
    stats.addProperty("transferred_locations", p.getTransferedLocCounter());
    o.add("summary", stats);
    // the changed and lost ones first: they need a review
    JsonArray sorted = new JsonArray();
    for (String st : new String[] { "lost", "changed", "unchanged" })
      for (int i = 0; i < reqs.size(); i++)
        if (st.equals(reqs.get(i).getAsJsonObject().get("status").getAsString()))
          sorted.add(reqs.get(i));
    o.add("requirements", sorted);
    JsonArray todo = new JsonArray();
    TreeNode links = db.getNode("/ToDoLinks");
    if (links != null)
      for (TreeNode t : links.getWorkChildren())
        if (t instanceof ToDoItem && newDoc.getQualifiedId().equals(t.attrs().getStringAttributeValue(ToDoItem.NEW_DOC))) {
          JsonObject ti = new JsonObject();
          ti.addProperty("path", t.getQualifiedId());
          ti.addProperty("action", t.attrs().getStringAttributeValue(ToDoItem.ATTR_TRANSF_ACTION));
          String st = t.attrs().getStringAttributeValue(ToDoItem.ATTR_STATUS);
          if (st != null)
            ti.addProperty("status", st);
          String req = t.attrs().getStringAttributeValue(ToDoItem.ATTR_REQ_LINK);
          try {
            TreeNode rn = req == null ? null : db.getNode(java.util.UUID.fromString(req));
            if (rn != null)
              ti.addProperty("requirement", rn.getQualifiedId());
          } catch (IllegalArgumentException e) {
            // not a UUID
          }
          todo.add(ti);
        }
    o.add("review_items", todo);
    String text = log.toString(StandardCharsets.UTF_8).trim();
    if (!text.isEmpty())
      o.addProperty("log", text.length() > 2000 ? text.substring(text.length() - 2000) : text);
    return o;
  }
}
