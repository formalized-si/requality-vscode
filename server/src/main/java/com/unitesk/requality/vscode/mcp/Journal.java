package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;

/**
 * The journal of the changes the agent made: one entry for each change (a batch of apply_changes, a document import, a
 * new document version) with what each operation changed. The person reviews it with review_changes; undo and redo
 * mark the entries. The entries are also appended to {@code <storage>/mcp-journal.jsonl}; a new server reads the file,
 * so the changes of earlier sessions stay visible (session "earlier"; undo reverts only changes of this session).
 */
final class Journal {
  private final List<JsonObject> entries = new ArrayList<>();
  private final File file;
  private int next = 1;
  /** The index of the first entry of this session. */
  private final int sessionStart;

  Journal(File storage) {
    this.file = storage == null ? null : new File(storage, "mcp-journal.jsonl");
    load();
    sessionStart = entries.size();
  }

  /** The entries of earlier sessions, with the undo and redo marks applied. */
  private void load() {
    if (file == null || !file.isFile())
      return;
    java.util.Map<Integer, JsonObject> byId = new java.util.HashMap<>();
    try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      String line;
      while ((line = r.readLine()) != null) {
        JsonObject o;
        try {
          o = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
          continue;
        }
        if (o.has("change")) {
          o.addProperty("session", "earlier");
          entries.add(o);
          byId.put(o.get("change").getAsInt(), o);
          next = Math.max(next, o.get("change").getAsInt() + 1);
        } else if (o.has("undone") && byId.containsKey(o.get("undone").getAsInt())) {
          byId.get(o.get("undone").getAsInt()).addProperty("state", "undone");
        } else if (o.has("redone") && byId.containsKey(o.get("redone").getAsInt())) {
          byId.get(o.get("redone").getAsInt()).addProperty("state", "done");
        } else if (o.has("state_of") && byId.containsKey(o.get("state_of").getAsInt())) {
          byId.get(o.get("state_of").getAsInt()).addProperty("state", o.get("state").getAsString());
        }
      }
    } catch (IOException e) {
      System.err.println("[mcp] cannot read the journal: " + e);
    }
  }

  /** Adds an entry; {@code label} is the label of its undo operation. */
  synchronized JsonObject add(String project, String tool, String label, String summary, JsonArray operations) {
    JsonObject e = new JsonObject();
    e.addProperty("change", next++);
    e.addProperty("time", Instant.now().toString());
    e.addProperty("project", project);
    e.addProperty("tool", tool);
    if (summary != null)
      e.addProperty("summary", summary);
    e.addProperty("label", label);
    e.add("operations", operations);
    e.addProperty("state", "done");
    entries.add(e);
    append(e);
    return e;
  }

  /** Marks the newest entry with this undo label as undone (or the newest undone one as redone). */
  synchronized JsonObject mark(String label, boolean undo) {
    for (int i = entries.size() - 1; i >= sessionStart; i--) {
      JsonObject e = entries.get(i);
      boolean done = "done".equals(e.get("state").getAsString());
      if (e.get("label").getAsString().equals(label) && done == undo) {
        e.addProperty("state", undo ? "undone" : "done");
        JsonObject m = new JsonObject();
        m.addProperty("time", Instant.now().toString());
        m.addProperty(undo ? "undone" : "redone", e.get("change").getAsInt());
        append(m);
        // the undo of a revert makes the reverted change done again
        if (e.has("reverts"))
          setState(e.get("reverts").getAsInt(), undo ? "done" : "reverted");
        return e;
      }
    }
    return null;
  }

  synchronized List<JsonObject> entries() {
    return new ArrayList<>(entries);
  }

  synchronized JsonObject entry(int change) {
    for (JsonObject e : entries)
      if (e.get("change").getAsInt() == change)
        return e;
    return null;
  }

  /** Sets the state of an entry (for example "reverted") and records it in the file. */
  synchronized void setState(int change, String state) {
    JsonObject e = entry(change);
    if (e == null)
      return;
    e.addProperty("state", state);
    JsonObject m = new JsonObject();
    m.addProperty("time", Instant.now().toString());
    m.addProperty("state_of", change);
    m.addProperty("state", state);
    append(m);
  }

  private void append(JsonObject e) {
    if (file == null)
      return;
    if (e.has("session")) {
      e = e.deepCopy();
      e.remove("session");
    }
    try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
      w.write(JsonRpcConnection.GSON.toJson(e));
      w.write('\n');
    } catch (IOException ex) {
      System.err.println("[mcp] cannot write the journal: " + ex);
    }
  }
}
