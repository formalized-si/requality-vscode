package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.services.ExplorerService;
import com.unitesk.requality.vscode.ui.ide.ContentTypeDecorator;
import com.unitesk.requality.vscode.ui.ide.LinkedResourceDecorator;
import com.unitesk.requality.vscode.ui.ide.ResourceFilterDecorator;
import com.unitesk.requality.vscode.ui.ide.SymlinkDecorator;
import com.unitesk.requality.vscode.ui.ide.VirtualResourceDecorator;
import com.unitesk.requality.vscode.ui.jface.viewers.Decoration;
import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * The lightweight decorators of org.eclipse.ui.ide ({@code org.eclipse.ui.decorators}) on the resources of the explorer:
 * a plain Eclipse project with a symbolic link to a file and a symbolic link to a folder. The overlays of the links are
 * composed PNG icons; a regular file keeps the file icon of the VS Code theme.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceDecoratorsTest {
  static ServerHarness h;
  static File plain;
  static File file;
  static File dir;
  static File linkFile;
  static File linkDir;
  static String projectItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "plain");
    file = new File(plain, "a.txt");
    dir = new File(plain, "dir");
    linkFile = new File(plain, "link.txt");
    linkDir = new File(plain, "linkdir");
    dir.mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects>"
        + "<buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(file.toPath(), "alpha");
    Files.writeString(new File(dir, "inner.txt").toPath(), "inner");
    Files.createSymbolicLink(linkFile.toPath(), file.toPath());
    Files.createSymbolicLink(linkDir.toPath(), dir.toPath());
    h.initialize(h.workspace);
    h.await("the plain project", v -> {
      try {
        return itemOf("plain") != null;
      } catch (Exception e) {
        return false;
      }
    });
    projectItem = itemOf("plain");
    assertNotNull(projectItem);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static JsonObject child(String parent, String label) throws Exception {
    for (JsonElement e : h.children(parent))
      if (e.getAsJsonObject().get("label").getAsString().equals(label))
        return e.getAsJsonObject();
    return null;
  }

  /** The decorated resource of a file or folder of the explorer. */
  static Object resource(File f) {
    return ExplorerService.element(ExplorerService.RESOURCE_PREFIX + f.getAbsolutePath());
  }

  @Test
  @Order(1)
  void regularFileKeepsTheFileIcon() throws Exception {
    JsonObject item = child(projectItem, "a.txt");
    assertNotNull(item);
    assertEquals("file", item.get("icon").getAsString());
    assertEquals("a.txt", item.get("label").getAsString());
  }

  @Test
  @Order(2)
  void symbolicLinkToFileGetsTheSymlinkOverlay() throws Exception {
    JsonObject item = child(projectItem, "link.txt");
    assertNotNull(item);
    String icon = item.get("icon").getAsString();
    assertTrue(new File(icon).isAbsolute(), icon);
    assertTrue(new File(icon).isFile(), icon);
    assertTrue(icon.endsWith(".png"), icon);
  }

  @Test
  @Order(3)
  void symbolicLinkToFolderGetsTheSymlinkOverlay() throws Exception {
    JsonObject item = child(projectItem, "linkdir");
    assertNotNull(item);
    String icon = item.get("icon").getAsString();
    assertTrue(new File(icon).isAbsolute(), icon);
    assertTrue(new File(icon).isFile(), icon);
    assertTrue(icon.endsWith(".png"), icon);
    assertFalse(icon.equals("folder"));
  }

  @Test
  @Order(4)
  void decoratorsAreDeclaredAsInEclipse() {
    String[][] expected = {
        // id, location
        { "org.eclipse.ui.LinkedResourceDecorator", "BOTTOM_RIGHT" },
        { "org.eclipse.ui.SymlinkDecorator", "TOP_RIGHT" },
        { "org.eclipse.ui.VirtualResourceDecorator", "BOTTOM_RIGHT" },
        { "org.eclipse.ui.ContentTypeDecorator", "REPLACE" },
        { "org.eclipse.ui.ResourceFilterDecorator", "BOTTOM_LEFT" } };
    for (String[] e : expected) {
      IConfigurationElement found = null;
      for (IConfigurationElement c : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.decorators"))
        if (e[0].equals(c.getAttribute("id")))
          found = c;
      assertNotNull(found, e[0]);
      assertEquals(e[1], found.getAttribute("location"), e[0]);
      assertEquals("true", found.getAttribute("lightweight"), e[0]);
    }
    // ResourceFilterDecorator is disabled by default in Eclipse (state="false")
    for (IConfigurationElement c : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.decorators"))
      if ("org.eclipse.ui.ResourceFilterDecorator".equals(c.getAttribute("id")))
        assertEquals("false", c.getAttribute("state"));
  }

  @Test
  @Order(5)
  void symlinkDecoratorAddsTheOverlayOnlyForSymbolicLinks() {
    Decoration d = new Decoration();
    d.setDefaultQuadrant(IDecoration.TOP_RIGHT);
    new SymlinkDecorator().decorate(resource(linkFile), d);
    assertEquals(1, d.getOverlays().size());
    assertEquals(IDecoration.TOP_RIGHT, d.getOverlays().get(0).quadrant);
    assertTrue(d.getOverlays().get(0).image.getPath().endsWith("symlink_ovr.png"));

    Decoration plainFile = new Decoration();
    new SymlinkDecorator().decorate(resource(file), plainFile);
    assertTrue(plainFile.getOverlays().isEmpty());
  }

  @Test
  @Order(6)
  void otherDecoratorsAddNothingForPlainResources() {
    Decoration d = new Decoration();
    new LinkedResourceDecorator().decorate(resource(file), d);
    new VirtualResourceDecorator().decorate(resource(dir), d);
    new ResourceFilterDecorator().decorate(resource(dir), d);
    new ContentTypeDecorator().decorate(resource(file), d);
    assertTrue(d.getOverlays().isEmpty());
  }
}
