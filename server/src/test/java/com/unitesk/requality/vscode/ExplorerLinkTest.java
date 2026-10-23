package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.port.eclipse.editors.EditorUtils;
import com.unitesk.requality.vscode.services.ExplorerService;

/**
 * Link with Editor of the Requality Explorer: the "Link with Editor" toggle of the VS Code view (explorer/setLinked)
 * is the linking state of the explorer's CommonNavigator, which EditorUtils.LINKED_PROVIDER reads.
 */
class ExplorerLinkTest {
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  private static void setLinked(boolean linked) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("linked", linked);
    h.call("explorer/setLinked", p);
  }

  @Test
  void linkWithEditorIsTheLinkingStateOfTheNavigator() throws Exception {
    setLinked(false);
    assertFalse(ExplorerService.navigator().isLinkingEnabled());
    assertFalse(EditorUtils.LINKED_PROVIDER.isLinked());
    setLinked(true);
    assertTrue(ExplorerService.navigator().isLinkingEnabled());
    assertTrue(EditorUtils.LINKED_PROVIDER.isLinked());
  }
}
