package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Drag and drop of resources in the Requality Explorer (ResourceDropAdapterAssistant of the non-Requality project
 * content of plugin.xml): files and folders dropped on a folder (or on a file: its folder) are moved, or copied, and
 * the move is undone by Edit > Undo; drops that Eclipse refuses are refused with its reason.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceDropTest {
  static ServerHarness h;
  static File plain;
  static String plainItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "plain");
    new File(plain, "docs/sub").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "docs/readme.txt").toPath(), "text");
    Files.writeString(new File(plain, "docs/sub/a.txt").toPath(), "a");
    Files.writeString(new File(plain, "top.txt").toPath(), "top");
    h.initialize(h.workspace);
    h.call("workspace/rescan", null);
    plainItem = h.await2("the plain project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if ("plain".equals(e.getAsJsonObject().get("label").getAsString()))
          return e.getAsJsonObject().get("id").getAsString();
      return null;
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String item(File f) {
    return "resource:" + f.getAbsolutePath();
  }

  static JsonElement drop(String operation, String target, String... sources) throws Exception {
    JsonObject p = new JsonObject();
    JsonArray s = new JsonArray();
    for (String x : sources)
      s.add(x);
    p.add("sources", s);
    p.addProperty("target", target);
    p.addProperty("operation", operation);
    return h.callAsync("explorer/drop", p).get(120, TimeUnit.SECONDS);
  }

  static void command(String command) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(plainItem);
    p.add("nodes", nodes);
    h.callAsync("command/execute", p).get(120, TimeUnit.SECONDS);
  }

  @Test
  @Order(1)
  void aFileDroppedOnAFolderIsMovedAndTheMoveIsUndone() throws Exception {
    File top = new File(plain, "top.txt");
    File moved = new File(plain, "docs/top.txt");
    assertEquals(true, drop("move", item(new File(plain, "docs")), item(top)).getAsBoolean());
    h.await("moved", v -> moved.isFile() && !top.exists());
    command("org.eclipse.ui.edit.undo");
    h.await("moved back", v -> top.isFile() && !moved.exists());
    assertEquals("top", Files.readString(top.toPath()));
  }

  @Test
  @Order(2)
  void aDropOnAFileGoesToItsFolderAndACopyDropCopies() throws Exception {
    File readme = new File(plain, "docs/readme.txt");
    // dropped on top.txt: the target is the folder of the file, the project
    assertEquals(true, drop("copy", item(new File(plain, "top.txt")), item(readme)).getAsBoolean());
    h.await("copied", v -> new File(plain, "readme.txt").isFile());
    assertTrue(readme.isFile(), "a copy keeps the source");
  }

  @Test
  @Order(3)
  void invalidDropsAreRefusedWithTheReasonOfEclipse() throws Exception {
    File docs = new File(plain, "docs");
    // a folder into its own subfolder (MoveFilesAndFoldersOperation: the source would be moved onto itself)
    JsonElement r = drop("move", item(new File(docs, "sub")), item(docs));
    assertTrue(r.isJsonPrimitive() && r.getAsJsonPrimitive().isString(), r.toString());
    assertEquals("Cannot move 'docs'. The source and destination are the same.", r.getAsString());
    r = drop("copy", item(new File(docs, "sub")), item(docs));
    assertEquals("Destination cannot be a descendent of the source.", r.getAsString());
    // a file into the folder it is in
    r = drop("move", item(docs), item(new File(docs, "readme.txt")));
    assertEquals("Cannot move 'readme.txt'. The source and destination are the same.", r.getAsString());
    // a project into a folder
    r = drop("move", item(docs), plainItem);
    assertEquals("Cannot drop a project into another project", r.getAsString());
    assertTrue(new File(docs, "sub").isDirectory());
    assertFalse(new File(docs, "sub/docs").exists());
  }

  @Test
  @Order(4)
  void persistentPropertiesMoveWithTheResource() throws Exception {
    // e.g. the editor chosen with Open With (IDE.setDefaultEditor): Eclipse keeps it when the file is moved
    com.unitesk.requality.vscode.ui.runtime.QualifiedName key = new com.unitesk.requality.vscode.ui.runtime.QualifiedName("test", "p");
    com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot root = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin
        .getWorkspace().getRoot();
    File a = new File(plain, "docs/sub/a.txt");
    root.getFileForLocation(com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(a.getAbsolutePath()))
        .setPersistentProperty(key, "v");
    File moved = new File(plain, "a.txt");
    assertEquals(true, drop("move", plainItem, item(a)).getAsBoolean());
    h.await("moved", v -> moved.isFile() && !a.exists());
    assertEquals("v", root.getFileForLocation(com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(moved.getAbsolutePath()))
        .getPersistentProperty(key));
    // a copy gets it too, a deleted file loses it
    assertEquals(true, drop("copy", item(new File(plain, "docs")), item(moved)).getAsBoolean());
    File copy = new File(plain, "docs/a.txt");
    h.await("copied", v -> copy.isFile());
    assertEquals("v", root.getFileForLocation(com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(copy.getAbsolutePath()))
        .getPersistentProperty(key));
  }
}
