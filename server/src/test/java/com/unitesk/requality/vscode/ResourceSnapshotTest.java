package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.IResourceSnapshot;
import com.unitesk.requality.vscode.ui.resources.undo.snapshot.ResourceSnapshotFactory;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * The resource snapshots of the undoable resource operations ({@code AbstractResourceSnapshot}): a file deleted and
 * created again from its snapshot gets back its contents and its local time stamp, as in Eclipse.
 */
class ResourceSnapshotTest {
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "Snap");
    plain.mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Snap</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "t.txt").toPath(), "stamp\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    h.await2("the plain project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Snap"))
          return e;
      return null;
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  @Test
  void aRestoredFileGetsBackItsLocalTimeStamp() throws Exception {
    File f = new File(plain, "t.txt");
    long stamp = 1_600_000_000_000L;
    assertTrue(f.setLastModified(stamp));
    IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(f.getAbsolutePath()));
    assertEquals(stamp, file.getLocalTimeStamp());
    // as the delete operation does: the snapshot before the delete, the contents from the history after it
    IResourceSnapshot<IFile> snapshot = ResourceSnapshotFactory.fromResource(file);
    file.delete(IResource.KEEP_HISTORY, null);
    snapshot.recordStateFromHistory(new NullProgressMonitor());
    assertFalse(f.exists());
    snapshot.createResource(new NullProgressMonitor());
    assertTrue(f.isFile());
    assertEquals("stamp\n", Files.readString(f.toPath()));
    assertEquals(stamp, f.lastModified(), "the local time stamp of the snapshot");
  }
}
