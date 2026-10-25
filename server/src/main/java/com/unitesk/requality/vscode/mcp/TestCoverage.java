package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tests.CoverageFromFile;
import com.unitesk.requality.tests.CoverageStorage;
import com.unitesk.requality.tests.TestErrorInfo;
import com.unitesk.requality.tests.TreeNodeCoverageInfo;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * test_coverage: the coverage of the requirements by tests, from a coverage file of the test runs (the file a coverage
 * source of Requality reads): the leaf requirements that tests cover, the ones no test covers, and the test failures with
 * the requirements they violate. It changes nothing.
 */
final class TestCoverage {
  private TestCoverage() {
  }

  /** The format of a coverage file, for the description of the tool. */
  static final String FORMAT = "<coverage><reqcoverage qid=\"/Requirements/A/B\"><covered_by uri=\"test/path\" "
      + "label=\"Test name\" hits=\"1\"/></reqcoverage><error name=\"...\" testuri=\"test/path\"><violates "
      + "qid=\"/Requirements/A/B\"/><description format=\"plain\">...</description></error></coverage>";

  static JsonObject report(TreeDB db, TreeNode under, File file, int limit) {
    CoverageFromFile src = new CoverageFromFile();
    src.setTargetStorage(new CoverageStorage(db));
    src.setSettings(file.toURI().toString());
    Map<String, TreeNodeCoverageInfo> cov = src.getCoverage();
    String errors = src.getLastErrors();
    if (cov == null || errors != null && !errors.isEmpty())
      throw new ToolError("The coverage file " + file + " cannot be read: " + Core.text(errors == null ? "" : errors));
    List<Requirement> leaves = new ArrayList<>();
    collect(under, leaves);
    Capped covered = new Capped(limit);
    Capped uncovered = new Capped(limit);
    for (Requirement r : leaves) {
      TreeNodeCoverageInfo info = cov.get(r.getQualifiedId());
      JsonObject o = item(r);
      if (info != null && info.getTestPaths().length > 0) {
        o.addProperty("tests", info.getTestPaths().length);
        int hits = info.getCntAll();
        if (hits >= 0)
          o.addProperty("hits", hits);
        JsonArray labels = new JsonArray();
        for (String p : info.getTestPaths()) {
          if (labels.size() >= 10)
            break;
          labels.add(info.getTestLabels().getOrDefault(p, p));
        }
        o.add("test_names", labels);
        covered.add(o);
      } else {
        uncovered.add(o);
      }
    }
    // the requirements of the file that the project does not have (renamed or deleted since the tests ran)
    Capped unknown = new Capped(limit);
    for (String qid : cov.keySet())
      if (find(db, qid) == null)
        unknown.add(new com.google.gson.JsonPrimitive(qid));
    Capped failures = new Capped(limit);
    Map<String, TestErrorInfo[]> errs = src.getErrors();
    if (errs != null)
      for (TestErrorInfo[] list : errs.values())
        for (TestErrorInfo e : list) {
          JsonObject o = new JsonObject();
          o.addProperty("name", e.getName());
          o.addProperty("test", e.getRawTestPath());
          String d = Core.text(e.getDescription());
          if (d != null && !d.isEmpty())
            o.addProperty("description", d.length() > 500 ? d.substring(0, 500) + " …" : d);
          JsonArray reqs = new JsonArray();
          if (e.getRequirementsQIDs() != null)
            for (String q : e.getRequirementsQIDs()) {
              TreeNode n = find(db, q);
              reqs.add(n != null ? n.getQualifiedId() : q);
            }
          o.add("violates", reqs);
          failures.add(o);
        }
    JsonObject r = new JsonObject();
    r.addProperty("file", file.getPath());
    r.addProperty("under", under.getQualifiedId());
    r.addProperty("leaves", leaves.size());
    r.addProperty("covered_percent", leaves.isEmpty() ? 0 : Math.round(covered.total * 1000.0 / leaves.size()) / 10.0);
    r.add("covered", covered.json());
    r.add("uncovered", uncovered.json());
    r.add("failures", failures.json());
    r.add("unknown_requirements", unknown.json());
    return r;
  }

  private static TreeNode find(TreeDB db, String qid) {
    try {
      TreeNode n = db.getNode(qid, Path.QID | Path.UV | Path.UUID);
      return n == null || n.isDisposed() ? null : n;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** The leaf requirements (no child requirements) under a node, the node too. */
  private static void collect(TreeNode n, List<Requirement> out) {
    boolean children = false;
    for (TreeNode c : n.getWorkChildren())
      if (c instanceof Requirement && !c.isDisposed()) {
        children = true;
        collect(c, out);
      }
    // the /Requirements folder is no requirement of its own
    boolean folder = n.getWorkParent() != null && n.getWorkParent().getWorkParent() == null;
    if (n instanceof Requirement && !children && !folder)
      out.add((Requirement) n);
  }

  private static JsonObject item(TreeNode n) {
    JsonObject o = new JsonObject();
    o.addProperty("path", n.getQualifiedId());
    if (n.getName() != null)
      o.addProperty("name", n.getName());
    return o;
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
}
