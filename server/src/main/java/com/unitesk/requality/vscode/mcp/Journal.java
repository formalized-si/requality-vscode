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
 * mark the entries. The entries are also appended to {@code <storage>/mcp-journal.jsonl}.
 */
final class Journal {
  private final List<JsonObject> entries = new ArrayList<>();
  private final File file;
  private int next = 1;

  Journal(File storage) {
    this.file = storage == null ? null : new File(storage, "mcp-journal.jsonl");
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
    for (int i = entries.size() - 1; i >= 0; i--) {
      JsonObject e = entries.get(i);
      boolean done = "done".equals(e.get("state").getAsString());
      if (e.get("label").getAsString().equals(label) && done == undo) {
        e.addProperty("state", undo ? "undone" : "done");
        JsonObject m = new JsonObject();
        m.addProperty("time", Instant.now().toString());
        m.addProperty(undo ? "undone" : "redone", e.get("change").getAsInt());
        append(m);
        return e;
      }
    }
    return null;
  }

  synchronized List<JsonObject> entries() {
    return new ArrayList<>(entries);
  }

  private void append(JsonObject e) {
    if (file == null)
      return;
    try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
      w.write(JsonRpcConnection.GSON.toJson(e));
      w.write('\n');
    } catch (IOException ex) {
      System.err.println("[mcp] cannot write the journal: " + ex);
    }
  }
}
