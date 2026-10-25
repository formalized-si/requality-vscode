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
    History h = history(db);
    History.Capture capture = new History.Capture(h.blobs());
    JsonArray ops = Core.transaction(db, label, true, true, capture, body);
    JsonObject e = h.add(db.getSemiProjectName(), tool, label, summary, ops, capture, reverts);
    synchronized (steps) {
      steps.add(new Object[] { capture.transaction, h, e.get("change").getAsInt() });
      while (steps.size() > STEPS)
        steps.remove(0);
    }
    return e;
  }

  /** Runs the body in a transaction that is rolled back: a check of a change. */
  static <T> T dryRun(TreeDB db, String label, Callable<T> body) throws Exception {
    return Core.transaction(db, label, true, false, body);
  }

  // ---- undo and redo

  /** Undo (or redo) of the newest undo step, when it is a change of the agent in this session. */
  JsonObject undoRedo(boolean undo) {
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
      boolean called = undo ? UndoRedoMech.callUndo() : UndoRedoMech.callRedo();
      // the operation moved to the other list when it was made
      boolean done = called && (undo ? oh.getRedoOperation(ctx) == op : oh.getUndoOperation(ctx) == op);
      return new Object[] { op.getLabel(), step, done };
    });
    if (r == null)
      throw new ToolError(undo ? "Nothing to undo" : "Nothing to redo");
    if (r[1] == null)
      throw new ToolError("The newest " + (undo ? "undo" : "redo") + " step (" + r[0] + ") is not a change of the agent in "
          + "this session; use revert_change for the changes of review_changes");
    Object[] step = (Object[]) r[1];
    History h = (History) step[1];
    int n = (Integer) step[2];
    if (!(Boolean) r[2])
      throw new ToolError((undo ? "Undo" : "Redo") + " of change " + n + " failed (see server_messages)");
    JsonObject e = h.entry(n);
    // the nodes the undo or redo brought back: the links to them are indexed again
    JsonObject states = h.states(n);
    if (states != null && e != null)
      History.relink(core.db(e.get("project").getAsString()), uuids(states));
    h.setState(n, undo ? "undone" : "done");
    // the undo of a revert brings the reverted change back, its redo reverts it again
    if (e != null && e.has("reverts"))
      h.setState(e.get("reverts").getAsInt(), undo ? "done" : "reverted");
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

  // ---- revert

  /**
   * Reverts a change: its nodes become as they were before it, with their UUIDs, in one transaction. Refused when a node
   * of the change is not as the change left it, unless {@code force}.
   */
  JsonObject revert(TreeDB db, int n, boolean force, boolean dryRun) throws Exception {
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
      Callable<JsonArray> body = () -> {
        History.Plan p = History.plan(db, states);
        if (!p.conflicts.isEmpty() && !force)
          throw new ToolError("Change " + n + " cannot be reverted: " + conflicts(p.conflicts)
              + ". Revert it anyway with force true: the later changes of these nodes are lost. Nothing was changed.");
        plan[0] = describe(db, states, p);
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
        h.setState(n, "reverted");
        // a revert of a revert brings the first change back
        if (e.has("reverts"))
          h.setState(e.get("reverts").getAsInt(), "done");
        r.addProperty("change", entry.get("change").getAsInt());
      }
      r.add("nodes", plan[0]);
      return r;
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
    if (n != null && !n.isDisposed())
      o.addProperty("path", n.getQualifiedId());
    else if (state != null)
      o.addProperty("id", state.get("id").getAsString());
    o.addProperty("uuid", u.toString());
    return o;
  }

  // ---- review

  /** The changes, newest first: a summary of each one, or all of one change ({@code change}). */
  JsonObject review(String project, int last, Integer change, boolean verbose) {
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
          return e.deepCopy();
      throw new ToolError("Project " + project + " has no change " + change);
    }
    all.sort((a, b) -> b.get("time").getAsString().compareTo(a.get("time").getAsString()));
    JsonArray out = new JsonArray();
    for (JsonObject e : all) {
      if (out.size() >= last)
        break;
      out.add(verbose ? e.deepCopy() : brief(e));
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
