package com.unitesk.requality.vscode.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.ToDoItem;

/**
 * trace_coverage: the traceability of a project as data. The requirements without document locations or without test
 * purposes (leaf requirements: no child requirements), the links that do not resolve, the checker problems, the
 * coverage of each document by locations, and the open review items of document updates (ToDoItem nodes).
 */
final class Coverage {
  private Coverage() {
  }

  /** A list of at most {@code limit} items with the total number. */
  private static final class Capped {
    final JsonArray items = new JsonArray();
    int total;
    final int limit;

    Capped(int limit) {
      this.limit = limit;
    }

    void add(com.google.gson.JsonElement e) {
      total++;
      if (items.size() < limit)
        items.add(e);
    }

    JsonObject json() {
      JsonObject o = new JsonObject();
      o.addProperty("total", total);
      o.add("items", items);
      return o;
    }
  }

  static JsonObject report(TreeDB db, TreeNode under, boolean refresh, boolean documents, int limit) throws Exception {
    List<Requirement> reqs = new ArrayList<>();
    collect(under, reqs);
    if (under instanceof Requirement && under.getWorkParent() != null && under.getWorkParent().getWorkParent() == null)
      reqs.remove(under); // the /Requirements folder is no requirement of its own
    int leaves = 0;
    Capped noLocations = new Capped(limit);
    Capped noTests = new Capped(limit);
    Capped broken = new Capped(limit);
    Capped problems = new Capped(limit);
    int errors = 0;
    int warnings = 0;
    for (Requirement r : reqs) {
      boolean leaf = r.getWorkChildren(Requirement.TYPE_NAME).isEmpty();
      if (leaf) {
        leaves++;
        if (r.getLocations(false).stream().noneMatch(l -> l != null && !l.isDisposed()))
          noLocations.add(item(r));
        if (r.getWorkChildren(TestPurpose.TYPE_NAME).isEmpty())
          noTests.add(item(r));
      }
      Map<String, ReferenceValue[]> links = RelManager.getLinksOrErrors(r, false);
      if (links != null)
        for (Map.Entry<String, ReferenceValue[]> e : links.entrySet()) {
          if (e.getKey().startsWith("_") || e.getValue() == null)
            continue;
          for (ReferenceValue rv : e.getValue())
            if (rv != null && rv.hasErrorsOnResolve(r.attrs())) {
              JsonObject b = item(r);
              b.addProperty("attribute", e.getKey());
              b.addProperty("ref", rv.getRaw());
              broken.add(b);
            }
        }
      if (refresh)
        r.updateCheckerProblems();
      IMark[] marks = r.getCheckerProblems();
      if (marks != null)
        for (IMark m : marks) {
          JsonObject p = Core.problem(m, r);
          if ("warning".equals(p.get("severity").getAsString()))
            warnings++;
          else
            errors++;
          problems.add(p);
        }
    }
    JsonObject o = new JsonObject();
    o.addProperty("under", under.getQualifiedId());
    JsonObject rq = new JsonObject();
    rq.addProperty("total", reqs.size());
    rq.addProperty("leaves", leaves);
    rq.add("leaves_without_locations", noLocations.json());
    rq.add("leaves_without_test_purposes", noTests.json());
    rq.add("broken_links", broken.json());
    o.add("requirements", rq);
    JsonObject pr = problems.json();
    pr.addProperty("errors", errors);
    pr.addProperty("warnings", warnings);
    o.add("problems", pr);
    if (documents) {
      JsonArray docs = new JsonArray();
      List<Document> all = new ArrayList<>();
      collectDocuments(db.getNode(Document.getTypeRootQId()), all);
      for (Document d : all) {
        DocumentText dt = DocumentText.of(d);
        int[] t = dt.coverageTotals();
        int full = 0;
        int partial = 0;
        int none = 0;
        for (DocumentText.Paragraph p : dt.paragraphs)
          switch (p.coverage()) {
            case "full":
              full++;
              break;
            case "partial":
              partial++;
              break;
            default:
              none++;
          }
        JsonObject dj = new JsonObject();
        dj.addProperty("document", d.getQualifiedId());
        dj.addProperty("locations", dt.locations.size());
        dj.addProperty("paragraphs", dt.paragraphs.size());
        dj.addProperty("covered_paragraphs", full);
        dj.addProperty("partly_covered_paragraphs", partial);
        dj.addProperty("uncovered_paragraphs", none);
        dj.addProperty("covered_text_percent", t[1] == 0 ? 0 : Math.round(t[0] * 1000.0 / t[1]) / 10.0);
        docs.add(dj);
      }
      o.add("documents", docs);
    }
    Capped todo = new Capped(limit);
    int completed = 0;
    TreeNode links = db.getNode("/ToDoLinks");
    if (links != null)
      for (TreeNode t : links.getWorkChildren())
        if (t instanceof ToDoItem && !t.isDisposed()) {
          // the completed ones are done; the open ones and the ones to check are listed
          if ("COMPLETED".equals(t.attrs().getStringAttributeValue(ToDoItem.ATTR_STATUS))) {
            completed++;
            continue;
          }
          JsonObject ti = new JsonObject();
          ti.addProperty("path", t.getQualifiedId());
          String req = t.attrs().getStringAttributeValue(ToDoItem.ATTR_REQ_LINK);
          TreeNode rn = null;
          try {
            rn = req == null ? null : db.getNode(java.util.UUID.fromString(req));
          } catch (IllegalArgumentException e) {
            // not a UUID
          }
          if (rn != null)
            ti.addProperty("requirement", rn.getQualifiedId());
          ti.addProperty("action", t.attrs().getStringAttributeValue(ToDoItem.ATTR_TRANSF_ACTION));
          ti.addProperty("old_document", t.attrs().getStringAttributeValue(ToDoItem.OLD_DOC));
          ti.addProperty("new_document", t.attrs().getStringAttributeValue(ToDoItem.NEW_DOC));
          String st = t.attrs().getStringAttributeValue(ToDoItem.ATTR_STATUS);
          if (st != null)
            ti.addProperty("status", st);
          todo.add(ti);
        }
    JsonObject ri = todo.json();
    ri.addProperty("completed", completed);
    o.add("review_items", ri);
    return o;
  }

  private static JsonObject item(TreeNode n) {
    JsonObject o = new JsonObject();
    o.addProperty("path", n.getQualifiedId());
    if (n.getName() != null)
      o.addProperty("name", n.getName());
    return o;
  }

  private static void collect(TreeNode n, List<Requirement> out) {
    if (n instanceof Requirement)
      out.add((Requirement) n);
    for (TreeNode c : n.getWorkChildren())
      if (c instanceof Requirement && !c.isDisposed())
        collect(c, out);
  }

  private static void collectDocuments(TreeNode n, List<Document> out) {
    if (n == null)
      return;
    for (TreeNode c : n.getWorkChildren()) {
      if (c == null || c.isDisposed())
        continue;
      if (c instanceof Document)
        out.add((Document) c);
      else
        collectDocuments(c, out);
    }
  }
}
