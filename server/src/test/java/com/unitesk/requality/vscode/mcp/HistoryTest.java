package com.unitesk.requality.vscode.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The history of the agent changes of a catalog stays for later sessions, with the node states of each change. */
class HistoryTest {
  static JsonObject state(String id, String name) {
    return JsonParser.parseString("{\"id\":\"" + id + "\",\"parent\":\"p\",\"attributes\":{\"_name\":{\"value\":{\"value\":\""
        + name + "\"}}},\"resources\":{}}").getAsJsonObject();
  }

  @Test
  void theChangesOfEarlierSessionsStay() throws Exception {
    File dir = Files.createTempDirectory("requality-history").toFile();
    History first = new History(dir, "/catalog");
    History.Capture c = new History.Capture(first.blobs());
    c.before.add("u1", state("A", "Alpha"));
    c.after.add("u1", state("A", "Alpha 2"));
    c.before.add("u2", JsonNull.INSTANCE);
    c.after.add("u2", state("B", "Beta"));
    JsonObject one = first.add("P", "apply_changes", "Agent: one", "one", new JsonArray(), c, 0);
    assertEquals(1, one.get("change").getAsInt());
    assertEquals(2, one.get("nodes").getAsInt());
    JsonObject two = first.add("P", "revert_change", "Revert change 1", null, new JsonArray(), new History.Capture(first.blobs()), 1);
    first.setState(1, "reverted");
    assertTrue(new File(dir, "changes/1.json").isFile());

    History second = new History(dir, "/catalog");
    List<JsonObject> e = second.entries();
    assertEquals(2, e.size());
    assertEquals("earlier", e.get(0).get("session").getAsString());
    assertEquals("reverted", e.get(0).get("state").getAsString());
    assertEquals(1, e.get(1).get("reverts").getAsInt());
    assertEquals(two.get("change").getAsInt(), e.get(1).get("change").getAsInt());
    JsonObject states = second.states(1);
    assertEquals("Alpha", states.getAsJsonObject("before").getAsJsonObject("u1").getAsJsonObject("attributes")
        .getAsJsonObject("_name").getAsJsonObject("value").get("value").getAsString());
    assertTrue(states.getAsJsonObject("before").get("u2").isJsonNull());
    assertNull(second.states(9));
    assertEquals(3, second.add("P", "apply_changes", "Agent: three", null, new JsonArray(), null, 0).get("change").getAsInt());
    // a line the last server did not finish is skipped
    Files.writeString(new File(dir, "journal.jsonl").toPath(), "{\"change\":4,\"ti", java.nio.file.StandardOpenOption.APPEND);
    History third = new History(dir, "/catalog");
    assertEquals(3, third.entries().size());
    // the next entry starts a new line
    assertEquals(4, third.add("P", "apply_changes", "Agent: four", null, new JsonArray(), null, 0).get("change").getAsInt());
    assertEquals(4, new History(dir, "/catalog").entries().size());
  }

  @Test
  void theStatesOfOldChangesGo() throws Exception {
    File dir = Files.createTempDirectory("requality-history-old").toFile();
    History h = new History(dir, "/catalog");
    History.Capture c = new History.Capture(h.blobs());
    JsonObject st = state("D", "Doc");
    JsonObject res = new JsonObject();
    res.addProperty("doc.xhtml", "abc");
    st.add("resources", res);
    c.before.add("u1", st);
    c.after.add("u1", JsonNull.INSTANCE);
    h.add("P", "apply_changes", "Agent: old", null, new JsonArray(), c, 0);
    Files.writeString(new File(h.blobs(), "abc").toPath(), "content");
    Files.writeString(new File(h.blobs(), "unused").toPath(), "content");
    // the change is two years old
    File journal = new File(dir, "journal.jsonl");
    String old = java.time.Instant.now().minus(java.time.Duration.ofDays(730)).toString();
    Files.writeString(journal.toPath(), Files.readString(journal.toPath()).replaceAll("\"time\":\"[^\"]*\"", "\"time\":\"" + old + "\""));
    History later = new History(dir, "/catalog");
    assertEquals(1, later.entries().size());
    assertNull(later.states(1));
    assertFalse(new File(h.blobs(), "abc").exists());
    assertFalse(new File(h.blobs(), "unused").exists());
  }

  @Test
  void statesCompareWithoutLineEnds() {
    JsonObject a = state("A", "Alpha\r\n");
    JsonObject b = state("A", "Alpha\n");
    assertTrue(History.same(a, b));
    assertTrue(History.same(null, null));
    assertFalse(History.same(a, null));
    assertFalse(History.same(a, state("A", "Alpha 2")));
    assertEquals("changed (attributes name)", History.difference(a, state("A", "Alpha 2")));
    assertEquals("renamed (A to B)", History.difference(a, state("B", "Alpha")));
  }
}
