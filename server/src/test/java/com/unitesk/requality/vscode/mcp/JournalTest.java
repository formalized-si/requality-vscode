package com.unitesk.requality.vscode.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** The journal of the agent changes stays for later sessions; undo marks only changes of its own session. */
class JournalTest {
  @Test
  void theChangesOfEarlierSessionsStay() throws Exception {
    File dir = Files.createTempDirectory("requality-journal").toFile();
    Journal first = new Journal(dir);
    first.add("P", "apply_changes", "Agent: one", "one", new JsonArray());
    first.add("P", "apply_changes", "Agent: two", "two", new JsonArray());
    assertEquals(2, first.mark("Agent: two", true).get("change").getAsInt());

    Journal second = new Journal(dir);
    List<JsonObject> e = second.entries();
    assertEquals(2, e.size());
    assertEquals("earlier", e.get(0).get("session").getAsString());
    assertEquals("done", e.get(0).get("state").getAsString());
    assertEquals("undone", e.get(1).get("state").getAsString());
    // the undo history of the earlier server is gone: its changes are not marked
    assertNull(second.mark("Agent: one", true));
    assertEquals(3, second.add("P", "apply_changes", "Agent: three", null, new JsonArray()).get("change").getAsInt());
    assertEquals(3, second.mark("Agent: three", true).get("change").getAsInt());
  }
}
