package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code IWorkspace.validateEdit} goes through the team core's file modification validator to the Team UI's default
 * validator (org.eclipse.team.core.defaultFileModificationValidator), which asks to make read-only files writable
 * ("Read-only File Encountered"), as in the Requality product (team.ui is installed).
 */
class FileModificationValidatorTest {
  static ServerHarness h;
  static File plain;
  static volatile String answer = "Yes";
  static volatile JsonObject asked;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "Valid");
    plain.mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Valid</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "r1.txt").toPath(), "one\n");
    Files.writeString(new File(plain, "r2.txt").toPath(), "two\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    h.await2("the plain project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Valid"))
          return e;
      return null;
    });
    h.answer("client/showMessage", p -> {
      asked = p;
      return answer;
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static IFile file(String name) {
    return ResourcesPlugin.getWorkspace().getRoot()
        .getFileForLocation(Path.fromOSString(new File(plain, name).getAbsolutePath()));
  }

  static IStatus validate(IFile f) throws Exception {
    CompletableFuture<IStatus> r = new CompletableFuture<>();
    Display.getDefault().asyncExec(() -> r.complete(ResourcesPlugin.getWorkspace().validateEdit(new IFile[] { f },
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell())));
    return r.get(60, TimeUnit.SECONDS);
  }

  @Test
  void aReadOnlyFileIsMadeWritableWhenTheUserAgrees() throws Exception {
    File r1 = new File(plain, "r1.txt");
    assertTrue(r1.setWritable(false));
    answer = "No";
    IStatus no = validate(file("r1.txt"));
    assertFalse(no.isOK(), "the file stays read-only");
    assertEquals("File /Valid/r1.txt is read-only.", no.getMessage());
    assertEquals("Read-only File Encountered", asked.get("title").getAsString(), asked.toString());
    assertTrue(asked.toString().contains("File '/Valid/r1.txt' is read-only. Do you wish to make it writable?"),
        asked.toString());
    assertTrue(file("r1.txt").isReadOnly(), "the file stays read-only");

    answer = "Yes";
    IStatus yes = validate(file("r1.txt"));
    assertTrue(yes.isOK(), yes.getMessage());
    assertFalse(file("r1.txt").isReadOnly(), "the file is made writable");
  }

  @Test
  void aWritableFileIsNotAsked() throws Exception {
    asked = null;
    assertTrue(validate(file("r2.txt")).isOK());
    assertEquals(null, asked);
  }
}
