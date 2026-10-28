package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;
import com.unitesk.requality.vscode.port.eclipse.operations.UndoableEclipseTreeDBOperation;
import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * The changes of the agent: each one is one transaction of a project (one undo step) and one entry of the history of
 * its catalog (see {@link History}). Undo and redo know the change of an undo step by its transaction; revert_change
 * reverts a change of any session.
 */
final class Session {
  /** At most this many undo steps of this session are known (the operation history of the workbench keeps fewer). */
  private static final int STEPS = 500;

  private final Core core;
  private final File storage;
  /** The undo steps of the changes of this session, oldest first: the transaction, the history, the change number. */
  private final List<Object[]> steps = new ArrayList<>();

  Session(Core core, File storage) {
    this.core = core;
    this.storage = storage;
  }

  /** The history of the catalog of a project. */
  History history(TreeDB db) {
    Workspace.Project p = core.workspace().project(db.getLocalName());
    if (p == null)
      throw new ToolError("Project " + db.getSemiProjectName() + " is not in the workspace");
    return History.of(storage, p.dir);
  }

  /**
   * Runs a change in one transaction and records it; returns its entry. The body returns the operations as the review
   * shows them.
   */
  JsonObject change(TreeDB db, String tool, String label, String summary, Callable<JsonArray> body) throws Exception {
    return change(db, tool, label, summary, body, 0);
  }

  private JsonObject change(TreeDB db, String tool, String label, String summary, Callable<JsonArray> body, int reverts)
      throws Exception {
    core.writable(db);
    History h = history(db);
    History.Capture capture = new History.Capture(h.blobs());
    JsonArray ops;
    try {
      ops = Core.transaction(db, label, true, true, capture, body);
    } finally {
      // a long change keeps the catalog in use until it ends
      core.used(db);
    }
    JsonObject e;
    try {
      e = h.add(db.getSemiProjectName(), tool, label, summary, ops, capture, reverts);
    } catch (RuntimeException x) {
      // the change is made: the agent must not make it again
      synchronized (steps) {
        steps.add(new Object[] { capture.transaction, h, 0 });
      }
      throw new ToolError("The change was made, but the history could not record it (" + Core.message(x) + "). Do not "
          + "repeat the call; undo takes the change back in this session");
    }
    synchronized (steps) {
      steps.add(new Object[] { capture.transaction, h, e.get("change").getAsInt() });
      while (steps.size() > STEPS)
        steps.remove(0);
    }
    if (!capture.unsaved.isEmpty())
      throw new ToolError("Change " + e.get("change").getAsInt() + " was made in the project, but the catalog files differ "
          + "from it: " + String.join("; ", capture.unsaved) + ". Load the project again with open_project and check these "
          + "nodes; revert_change " + e.get("change").getAsInt() + " takes the change back");
    return e;
  }

  /** Runs the body in a transaction that is rolled back: a check of a change. */
  static <T> T dryRun(TreeDB db, String label, Callable<T> body) throws Exception {
    return Core.transaction(db, label, true, false, body);
  }

  // ---- undo and redo

  /**
   * Undo (or redo) of the newest undo step, when it is a change of the agent in this session ({@code project}: and of
   * this project; null: any).
   */
  JsonObject undoRedo(boolean undo, String project) {
    Object[] r = Display.getDefault().syncCall(() -> {
      IOperationHistory oh = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
      com.unitesk.requality.vscode.ui.commands.operations.IUndoContext ctx = PlatformUI.getWorkbench().getOperationSupport()
          .getUndoContext();
      IUndoableOperation op = undo ? oh.getUndoOperation(ctx) : oh.getRedoOperation(ctx);
      if (op == null)
        return null;
      Object[] step = step(op);
      if (step == null)
        return new Object[] { op.getLabel(), null, false };
      String of = projectOf(step);
      if (project != null && of != null && !project.equals(of) && !project.equals(localName(of)))
        return new Object[] { op.getLabel(), step, false, of };
      boolean called = undo ? UndoRedoMech.callUndo() : UndoRedoMech.callRedo();
      // the operation moved to the other list when it was made
      boolean done = called && (undo ? oh.getRedoOperation(ctx) == op : oh.getUndoOperation(ctx) == op);
      return new Object[] { op.getLabel(), step, done };
    });
    if (r == null)
      throw new ToolError((undo ? "Nothing to undo" : "Nothing to redo") + lastStepHint(undo));
    if (r[1] == null)
      throw new ToolError("The newest " + (undo ? "undo" : "redo") + " step (" + r[0] + ") is not a change of the agent in "
          + "this session; use revert_change for the changes of review_changes");
    Object[] step = (Object[]) r[1];
    History h = (History) step[1];
    int n = (Integer) step[2];
    if (r.length > 3)
      throw new ToolError("The newest " + (undo ? "undo" : "redo") + " step is change " + n + " of project " + r[3] + ": "
          + (undo ? "undo" : "redo") + " takes the newest step first. For project " + project + " use revert_change");
    if (!(Boolean) r[2])
      throw new ToolError((undo ? "Undo" : "Redo") + " of change " + n + " failed (see server_messages)");
    JsonObject e = h.entry(n);
    // the nodes the undo or redo brought back: the links to them are indexed again
    JsonObject states = n > 0 ? h.states(n) : null;
    if (states != null && e != null)
      History.relink(core.db(e.get("project").getAsString()), uuids(states));
    if (e != null) {
      h.setState(n, undo ? "undone" : "done");
      // the undo of a revert brings the reverted change back, its redo reverts it again
      propagate(h, e);
    }
    JsonObject o = new JsonObject();
    o.addProperty(undo ? "undone" : "redone", n);
    o.addProperty("project", e != null ? e.get("project").getAsString() : null);
    o.addProperty("label", (String) r[0]);
    if (e != null && e.has("summary"))
      o.add("summary", e.get("summary"));
    return o;
  }

  private Object[] step(IUndoableOperation op) {
    if (!(op instanceof UndoableEclipseTreeDBOperation))
      return null;
    TransactionStorage ts = ((UndoableEclipseTreeDBOperation) op).getCoreOperation().getTransaction();
    synchronized (steps) {
      for (int i = steps.size() - 1; i >= 0; i--)
        if (steps.get(i)[0] == ts)
          return steps.get(i);
    }
    return null;
  }

  /** The project of the change of an undo step (null: unknown). */
  private static String projectOf(Object[] step) {
    try {
      JsonObject e = ((History) step[1]).entry((Integer) step[2]);
      return e == null ? null : e.get("project").getAsString();
    } catch (RuntimeException x) {
      return null;
    }
  }

  /** The folder name of a project of this name (the projects are named by either). */
  private String localName(String project) {
    try {
      return core.project(project).localName;
    } catch (RuntimeException e) {
      return project;
    }
  }

  /**
   * When this session made changes but the undo history has none (the catalog was loaded again: another session used
   * it, or it changed on disk): the change to revert instead.
   */
  private String lastStepHint(boolean undo) {
    if (!undo)
      return "";
    Object[] last;
    synchronized (steps) {
      last = steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }
    if (last == null || (Integer) last[2] <= 0)
      return "";
    History h = (History) last[1];
    JsonObject e = h.entry((Integer) last[2]);
    if (e == null || !"done".equals(e.get("state").getAsString()))
      return "";
    return ": the undo steps ended when the catalog was loaded again (another session used it, or it changed on disk). "
        + "Take back your last change with revert_change (project " + e.get("project").getAsString() + ", change "
        + last[2] + ")";
  }

  // ---- revert

  /**
   * Reverts a change: its nodes become as they were before it, with their UUIDs, in one transaction. Refused when a node
   * of the change is not as the change left it, unless {@code force}.
   */
  JsonObject revert(TreeDB db, int n, boolean force, boolean dryRun, boolean verbose) throws Exception {
    History h = history(db);
    synchronized (h) {
      JsonObject e = h.entry(n);
      if (e == null)
        throw new ToolError("Project " + db.getSemiProjectName() + " has no change " + n + " (see review_changes)");
      String state = e.get("state").getAsString();
      if (state.equals("undone"))
        throw new ToolError("Change " + n + " is undone (redo brings it back)");
      if (state.equals("reverted"))
        throw new ToolError("Change " + n + " is reverted already");
      JsonObject states = h.states(n);
      if (states == null)
        throw new ToolError("Change " + n + " has no saved node states and cannot be reverted");
      String label = "Revert change " + n;
      JsonArray[] plan = { null };
      JsonArray[] broken = { null };
      Callable<JsonArray> body = () -> {
        History.Plan p = History.plan(db, states);
        if (!p.conflicts.isEmpty() && !force) {
          List<Integer> later = later(h, n, p.conflictNodes);
          throw new ToolError("Change " + n + " cannot be reverted: " + conflicts(p.conflicts) + ". "
              + (later.isEmpty() ? "No later change of the agent made this (VS Code or another editor did). "
                  : "Later changes of these nodes: " + later + ": revert them first, newest first. ")
              + "Or revert it anyway with force true: the later changes of these nodes are lost and such links break. "
              + "Nothing was changed.");
        }
        plan[0] = describe(db, states, p);
        broken[0] = p.brokenLinks;
        if (!dryRun)
          History.apply(db, states, p, h.blobs());
        return plan[0];
      };
      JsonObject r = new JsonObject();
      r.addProperty("project", db.getSemiProjectName());
      r.addProperty("reverted_change", n);
      if (dryRun) {
        dryRun(db, label, body);
        r.addProperty("dry_run", true);
        r.addProperty("note", "Nothing was changed. The change can be reverted.");
      } else {
        String summary = "Revert of change " + n + (e.has("summary") ? ": " + e.get("summary").getAsString() : "");
        JsonObject entry = change(db, "revert_change", label, summary, body, n);
        History.relink(db, uuids(states));
        // the reverted change, and the change a reverted revert reverts (a chain)
        propagate(h, entry);
        r.addProperty("change", entry.get("change").getAsInt());
      }
      summarize(r, plan[0], verbose);
      if (broken[0] != null && broken[0].size() > 0) {
        r.add("broken_links", first(broken[0], verbose ? Integer.MAX_VALUE : SHOWN));
        r.addProperty("broken_link_count", broken[0].size());
      }
      return r;
    }
  }

  /** A revert result lists this many nodes (all with verbose). */
  static final int SHOWN = 20;

  /** The counts of the nodes of a revert by what it does to them, and the first nodes (all with verbose). */
  private static void summarize(JsonObject r, JsonArray nodes, boolean verbose) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String k : new String[] { "restore", "recreate", "delete" })
      counts.put(k, 0);
    for (JsonElement x : nodes)
      counts.merge(x.getAsJsonObject().get("op").getAsString(), 1, Integer::sum);
    JsonObject c = new JsonObject();
    c.addProperty("restored", counts.get("restore"));
    c.addProperty("recreated", counts.get("recreate"));
    c.addProperty("deleted", counts.get("delete"));
    r.add("counts", c);
    r.add("nodes", first(nodes, verbose ? Integer.MAX_VALUE : SHOWN));
    if (!verbose && nodes.size() > SHOWN)
      r.addProperty("more_nodes", nodes.size() - SHOWN + " more (verbose true lists all)");
  }

  private static JsonArray first(JsonArray a, int n) {
    if (a.size() <= n)
      return a;
    JsonArray out = new JsonArray();
    for (int i = 0; i < n; i++)
      out.add(a.get(i));
    return out;
  }

  /** The later changes (done) of the agent that touched any of the nodes, in the order they were made. */
  private static List<Integer> later(History h, int n, java.util.Set<UUID> nodes) {
    List<Integer> out = new ArrayList<>();
    for (JsonObject e : h.entries()) {
      int c = e.get("change").getAsInt();
      if (c <= n || !"done".equals(e.get("state").getAsString()))
        continue;
      try {
        JsonObject st = h.states(c);
        if (st == null)
          continue;
        for (UUID u : nodes)
          if (st.getAsJsonObject("after").has(u.toString())) {
            out.add(c);
            break;
          }
      } catch (RuntimeException x) {
        // states that cannot be read: not listed
      }
    }
    return out;
  }

  /**
   * The state of the changes a revert reverts: while the revert is done, the change it reverts is "reverted"; else it is
   * done again. A change that is a revert decides the state of the change it reverts in turn.
   */
  private static void propagate(History h, JsonObject e) {
    for (int guard = 0; e != null && e.has("reverts") && guard < 1000; guard++) {
      JsonObject target = h.entry(e.get("reverts").getAsInt());
      if (target == null)
        return;
      String want = "done".equals(e.get("state").getAsString()) ? "reverted" : "done";
      if (!want.equals(target.get("state").getAsString()))
        h.setState(target.get("change").getAsInt(), want);
      e = target;
    }
  }

  /** The nodes of the states of a change. */
  private static List<UUID> uuids(JsonObject states) {
    List<UUID> out = new ArrayList<>();
    for (String k : states.getAsJsonObject("after").keySet())
      out.add(UUID.fromString(k));
    return out;
  }

  private static String conflicts(List<String> c) {
    List<String> shown = c.size() > 20 ? c.subList(0, 20) : c;
    return String.join("; ", shown) + (c.size() > 20 ? "; and " + (c.size() - 20) + " more" : "");
  }

  /** What the revert does to each node: restored (changed back), recreated (deleted by the change) or deleted. */
  private static JsonArray describe(TreeDB db, JsonObject states, History.Plan p) {
    JsonArray out = new JsonArray();
    JsonObject before = states.getAsJsonObject("before");
    for (UUID u : p.recreate)
      out.add(node("recreate", db, u, before.getAsJsonObject(u.toString())));
    for (UUID u : p.restore)
      out.add(node("restore", db, u, before.getAsJsonObject(u.toString())));
    for (UUID u : p.delete)
      out.add(node("delete", db, u, null));
    return out;
  }

  private static JsonObject node(String what, TreeDB db, UUID u, JsonObject state) {
    JsonObject o = new JsonObject();
    o.addProperty("op", what);
    TreeNode n = db.getNode(u);
    if (n != null && !n.isDisposed()) {
      o.addProperty("path", n.getQualifiedId());
      o.addProperty("type", n.getType());
      if (n instanceof com.unitesk.requality.nodetype.Location) {
        // a location is known by its text and its document, not by its UUID
        String d = n.getDescription();
        if (d != null)
          o.addProperty("text", Core.text(d).replaceAll("\\s+", " "));
        TreeNode doc = n.getWorkParent();
        if (doc != null)
          o.addProperty("document", doc.getQualifiedId());
      }
    } else if (state != null) {
      o.addProperty("id", state.get("id").getAsString());
      String type = attribute(state, "_type");
      if (type != null)
        o.addProperty("type", type);
      String d = attribute(state, "_description");
      if ("Location".equals(type) && d != null)
        o.addProperty("text", Core.text(d).replaceAll("\\s+", " "));
      TreeNode parent = state.has("parent") ? db.getNode(UUID.fromString(state.get("parent").getAsString())) : null;
      if (parent != null && !parent.isDisposed())
        o.addProperty("parent", parent.getQualifiedId());
    }
    o.addProperty("uuid", u.toString());
    return o;
  }

  /** The value of a plain attribute of a saved state, or null. */
  private static String attribute(JsonObject state, String key) {
    try {
      JsonElement v = state.getAsJsonObject("attributes").getAsJsonObject(key).getAsJsonObject("value").get("value");
      return v != null && v.isJsonPrimitive() ? v.getAsString() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  // ---- review

  /**
   * One page of a change: the operations from {@code offset}, at most {@code limit}, and of the long lists of an operation
   * (the requirements and new sentences of a document update) the items from {@code offset}, at most {@code limit}.
   */
  static JsonObject page(JsonObject e, int offset, int limit) {
    JsonArray ops = e.getAsJsonArray("operations");
    if (ops == null)
      return e;
    boolean cut = false;
    if (ops.size() <= limit) {
      // a few operations (an update, an import): their long lists are paged
      for (JsonElement x : ops) {
        if (!x.isJsonObject())
          continue;
        JsonObject op = x.getAsJsonObject();
        for (String k : new ArrayList<>(op.keySet())) {
          if (!op.get(k).isJsonArray() || op.getAsJsonArray(k).size() <= limit && offset == 0)
            continue;
          JsonArray a = op.getAsJsonArray(k);
          op.add(k, slice(a, offset, limit));
          op.addProperty(k + "_total", a.size());
          cut |= offset + limit < a.size();
        }
      }
    } else {
      e.add("operations", slice(ops, offset, limit));
      e.addProperty("operations_total", ops.size());
      cut = offset + limit < ops.size();
    }
    if (cut)
      e.addProperty("next_offset", offset + limit);
    return e;
  }

  private static JsonArray slice(JsonArray a, int offset, int limit) {
    JsonArray out = new JsonArray();
    for (int i = Math.max(0, offset); i < a.size() && i < offset + limit; i++)
      out.add(a.get(i));
    return out;
  }

  /** The changes, newest first: a summary of each one, or all of one change ({@code change}). */
  JsonObject review(String project, int last, Integer change, boolean verbose, int offset, int limit) {
    List<JsonObject> all = new ArrayList<>();
    Map<String, History> histories = new LinkedHashMap<>();
    for (Workspace.Project p : core.workspace().projects()) {
      if (!p.requality)
        continue;
      String name = com.unitesk.requality.tool.CoreUtils.getProjectName(p.dir);
      if (project != null && !project.equals(name) && !project.equals(p.localName))
        continue;
      histories.put(name != null ? name : p.localName, History.of(storage, p.dir));
    }
    if (project != null && histories.isEmpty())
      core.project(project); // the error that lists the projects
    for (History h : histories.values())
      all.addAll(h.entries());
    if (change != null) {
      if (project == null)
        throw new ToolError("Give the project of change " + change);
      for (JsonObject e : all)
        if (e.get("change").getAsInt() == change)
          return page(e.deepCopy(), offset, limit);
      throw new ToolError("Project " + project + " has no change " + change);
    }
    all.sort((a, b) -> b.get("time").getAsString().compareTo(a.get("time").getAsString()));
    JsonArray out = new JsonArray();
    for (JsonObject e : all) {
      if (out.size() >= last)
        break;
      // verbose: the operations of each change, at most 20 of each (review_changes with a change pages the rest)
      out.add(verbose ? page(e.deepCopy(), 0, 20) : brief(e));
    }
    JsonObject r = new JsonObject();
    r.add("changes", out);
    r.addProperty("total", all.size());
    if (!verbose && out.size() > 0)
      r.addProperty("hint", "Give project and change for the operations of one change, with the values before and after");
    return r;
  }

  /** An entry without its operations: what kinds of operations it has and the first nodes. */
  private static JsonObject brief(JsonObject e) {
    JsonObject o = new JsonObject();
    for (String k : new String[] { "change", "time", "project", "tool", "summary", "state", "session", "reverts" })
      if (e.has(k))
        o.add(k, e.get(k));
    Map<String, Integer> kinds = new LinkedHashMap<>();
    JsonArray paths = new JsonArray();
    int ops = 0;
    if (e.has("operations"))
      for (JsonElement x : e.getAsJsonArray("operations")) {
        if (!x.isJsonObject())
          continue;
        ops++;
        JsonObject op = x.getAsJsonObject();
        String kind = op.has("op") ? op.get("op").getAsString() : "?";
        kinds.merge(kind, 1, Integer::sum);
        if (op.has("path") && paths.size() < 10 && !paths.contains(op.get("path")))
          paths.add(op.get("path"));
      }
    JsonObject k = new JsonObject();
    kinds.forEach(k::addProperty);
    o.addProperty("operations", ops);
    o.add("kinds", k);
    o.add("nodes", paths);
    return o;
  }
}
