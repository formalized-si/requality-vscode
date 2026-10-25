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
      + "qid=\"/Requirements/A/B\"/><description format=\"plain\">...</description></error></coverage>. A qid is the path, "
      + "the UUID, the user-visible id (with the id prefix of the project) or the id of a requirement that one node has.";

  static JsonObject report(TreeDB db, TreeNode under, File file, int limit) {
    CoverageFromFile src = new CoverageFromFile();
    src.setTargetStorage(new CoverageStorage(db));
    src.setSettings(file.toURI().toString());
    Map<String, TreeNodeCoverageInfo> cov = src.getCoverage();
    String errors = src.getLastErrors();
    if (cov == null || errors != null && !errors.isEmpty())
      throw new ToolError("The coverage file " + file + " cannot be read: " + Core.text(errors == null ? "" : errors));
    // the requirements of the file by their nodes (a path, a UUID, a user-visible id or an id that one node has); the
    // keys that name no node of the project are unknown
    Map<String, TreeNodeCoverageInfo> byPath = new java.util.HashMap<>();
    List<String> unknownKeys = new ArrayList<>();
    for (Map.Entry<String, TreeNodeCoverageInfo> e : cov.entrySet()) {
      TreeNode n = find(db, e.getKey());
      if (n == null)
        unknownKeys.add(e.getKey());
      else
        byPath.putIfAbsent(n.getQualifiedId(), e.getValue());
    }
    List<Requirement> leaves = new ArrayList<>();
    collect(under, leaves);
    Capped covered = new Capped(limit);
    Capped uncovered = new Capped(limit);
    for (Requirement r : leaves) {
      TreeNodeCoverageInfo info = byPath.get(r.getQualifiedId());
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
    for (String qid : unknownKeys)
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

  /**
   * A copy of a coverage file (in the folder {@code dir}) whose requirements are the paths of their nodes: the reports
   * of the core know a requirement by its path, the file can name it as the tools do. Null when the file names only
   * paths.
   */
  static File withPaths(TreeDB db, File file, File dir) throws Exception {
    javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setXIncludeAware(false);
    f.setExpandEntityReferences(false);
    org.w3c.dom.Document d = f.newDocumentBuilder().parse(file);
    boolean changed = false;
    for (String tag : new String[] { "reqcoverage", "violates" }) {
      org.w3c.dom.NodeList l = d.getElementsByTagName(tag);
      for (int i = 0; i < l.getLength(); i++) {
        org.w3c.dom.Element e = (org.w3c.dom.Element) l.item(i);
        String qid = e.getAttribute("qid");
        TreeNode n = qid.isEmpty() ? null : find(db, qid);
        if (n != null && !n.getQualifiedId().equals(qid)) {
          e.setAttribute("qid", n.getQualifiedId());
          changed = true;
        }
      }
    }
    if (!changed)
      return null;
    dir.mkdirs();
    File out = File.createTempFile("coverage-", ".xml", dir);
    out.deleteOnExit();
    javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
    t.transform(new javax.xml.transform.dom.DOMSource(d), new javax.xml.transform.stream.StreamResult(out));
    return out;
  }

  /** A requirement of a coverage file: as the tools find a node (path, UUID, user-visible id, id alone when unique). */
  private static TreeNode find(TreeDB db, String qid) {
    try {
      return Core.node(db, qid);
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
