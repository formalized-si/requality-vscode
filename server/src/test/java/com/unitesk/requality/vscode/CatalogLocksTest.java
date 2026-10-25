package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.CatalogLocks;

/**
 * One Requality server per catalog (CatalogLocks): a catalog that another process holds (the server of a VS Code window
 * or the headless MCP server) is not loaded; the project tells why.
 */
class CatalogLocksTest {
  /** A process that holds the lock of a catalog until its input ends. */
  public static final class Holder {
    public static void main(String[] args) throws Exception {
      CatalogLocks locks = new CatalogLocks(new File(args[0]));
      System.out.println(locks.acquire(new File(args[1])) ? "locked" : "busy");
      System.out.flush();
      System.in.read();
    }
  }

  static Process hold(File lockDir, File catalog) throws Exception {
    Process p = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp",
        System.getProperty("java.class.path"), Holder.class.getName(), lockDir.getPath(), catalog.getPath())
        .redirectError(ProcessBuilder.Redirect.INHERIT).start();
    String line = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)).readLine();
    assertEquals("locked", line);
    return p;
  }

  @Test
  void aCatalogHasOneHolder() throws Exception {
    File root = Files.createTempDirectory("requality-locks").toFile();
    File locks = new File(root, "locks");
    File catalog = new File(root, "catalog");
    catalog.mkdirs();
    Process other = hold(locks, catalog);
    try {
      java.lang.reflect.Constructor<CatalogLocks> c = CatalogLocks.class.getDeclaredConstructor(File.class, long.class);
      c.setAccessible(true);
      CatalogLocks mine = c.newInstance(locks, 500L);
      assertFalse(mine.acquire(catalog), "the other process holds the catalog");
      other.getOutputStream().close();
      assertTrue(other.waitFor(30, TimeUnit.SECONDS));
      assertTrue(mine.acquire(catalog), "the lock ends with the process");
      assertTrue(mine.acquire(catalog), "a second acquire of the holder");
      mine.release(catalog);
      Process again = hold(locks, catalog);
      again.getOutputStream().close();
      again.waitFor(30, TimeUnit.SECONDS);
    } finally {
      other.destroyForcibly();
    }
  }

  @Test
  void aServerDoesNotLoadACatalogOfAnotherProcess() throws Exception {
    ServerHarness h = new ServerHarness();
    File catalog = TestCatalogs.copyEtsi(h.workspace, "TS103096");
    Process other = hold(new File(System.getProperty("requality.locks")), catalog);
    try {
      h.initialize(h.workspace);
      JsonArray ps = h.awaitProjects(1);
      JsonObject p = ps.get(0).getAsJsonObject();
      assertEquals("broken", p.get("state").getAsString(), p.toString());
      assertTrue(p.get("error").getAsString().contains("another Requality process"), p.toString());
      // the other process ends: a new load of the catalog takes the lock and clears the error
      other.getOutputStream().close();
      assertTrue(other.waitFor(30, TimeUnit.SECONDS));
      assertTrue(h.server().workspace().loadProject(catalog));
      long end = System.currentTimeMillis() + 180_000;
      String state;
      do {
        Thread.sleep(200);
        p = ((JsonObject) h.call("workspace/projects", null)).getAsJsonArray("projects").get(0).getAsJsonObject();
        state = p.get("state").getAsString();
      } while (!state.equals("ready") && System.currentTimeMillis() < end);
      assertEquals("ready", state, p.toString());
      assertFalse(p.has("error"), p.toString());
    } finally {
      other.destroyForcibly();
      h.close();
    }
  }
}
