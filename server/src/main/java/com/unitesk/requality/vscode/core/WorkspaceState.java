package com.unitesk.requality.vscode.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.resources.FileMoves;

/**
 * What the Eclipse workspace keeps about its projects in its metadata ({@code .metadata/.plugins/org.eclipse.core.resources}),
 * kept per VS Code workspace: the closed projects, the projects removed from the workspace without deleting their
 * content, the projects located outside the workspace folders, the projects whose builders ran (an Eclipse builder
 * without a saved state gets a full build) and whether the workspace builds automatically.
 */
final class WorkspaceState {
  private final File file;
  private final Set<String> closed = new LinkedHashSet<>();
  private final Set<String> removed = new LinkedHashSet<>();
  private final Set<String> external = new LinkedHashSet<>();
  private final Set<String> built = new LinkedHashSet<>();
  private boolean autoBuilding = true;

  private WorkspaceState(File file) {
    this.file = file;
  }

  /** The key of a project folder: its canonical path. */
  static String key(File dir) {
    try {
      return dir.getCanonicalPath();
    } catch (IOException e) {
      return dir.getAbsolutePath();
    }
  }

  static WorkspaceState load(File file) {
    // the working sets of the workspace are kept next to its state (VSCODE: see WorkingSetManager.setWorkspaceStateFile)
    com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager.setWorkspaceStateFile(file);
    // and the state of the Requality Explorer view (its memento)
    com.unitesk.requality.vscode.services.ExplorerViewState.setWorkspaceStateFile(file);
    // the persistent markers (bookmarks, tasks) and the local history of the files of this workspace
    com.unitesk.requality.vscode.ui.resources.MarkerStore.setWorkspaceStateFile(file);
    com.unitesk.requality.vscode.ui.resources.FileHistory.setWorkspaceStateFile(file);
    WorkspaceState s = new WorkspaceState(file);
    if (file == null || !file.isFile())
      return s;
    try {
      JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
          .getAsJsonObject();
      read(o, "closed", s.closed);
      read(o, "removed", s.removed);
      read(o, "external", s.external);
      read(o, "built", s.built);
      if (o.has("autoBuilding"))
        s.autoBuilding = o.get("autoBuilding").getAsBoolean();
    } catch (RuntimeException | IOException e) {
      RLogger.logInfo("Cannot read the workspace state " + file + ": " + e);
    }
    return s;
  }

  private static void read(JsonObject o, String name, Set<String> into) {
    if (o.has(name) && o.get(name).isJsonArray())
      for (JsonElement e : o.getAsJsonArray(name))
        into.add(e.getAsString());
  }

  private static JsonArray array(Set<String> s) {
    JsonArray a = new JsonArray();
    s.forEach(a::add);
    return a;
  }

  private synchronized void save() {
    if (file == null)
      return;
    JsonObject o = new JsonObject();
    o.add("closed", array(closed));
    o.add("removed", array(removed));
    o.add("external", array(external));
    o.add("built", array(built));
    o.addProperty("autoBuilding", autoBuilding);
    try {
      File dir = file.getAbsoluteFile().getParentFile();
      if (dir != null)
        dir.mkdirs();
      File tmp = new File(dir, file.getName() + ".tmp");
      Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
      FileMoves.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      RLogger.logError(e);
    }
  }

  synchronized boolean isClosed(File dir) {
    return closed.contains(key(dir));
  }

  synchronized boolean isRemoved(File dir) {
    return removed.contains(key(dir));
  }

  synchronized Set<String> external() {
    return new LinkedHashSet<>(external);
  }

  synchronized boolean isBuilt(File dir) {
    return built.contains(key(dir));
  }

  synchronized boolean isAutoBuilding() {
    return autoBuilding;
  }

  synchronized void setClosed(File dir, boolean value) {
    if (value ? closed.add(key(dir)) : closed.remove(key(dir)))
      save();
  }

  synchronized void setBuilt(File dir, boolean value) {
    if (value ? built.add(key(dir)) : built.remove(key(dir)))
      save();
  }

  synchronized void setAutoBuilding(boolean value) {
    if (autoBuilding != value) {
      autoBuilding = value;
      save();
    }
  }

  /** A project (re)enters the workspace at a folder; outside the workspace folders it is an external location. */
  synchronized void added(File dir, boolean externalLocation) {
    String k = key(dir);
    boolean changed = removed.remove(k);
    changed |= externalLocation ? external.add(k) : external.remove(k);
    if (changed)
      save();
  }

  /** A project leaves the workspace; its content stays on disk unless {@code deleted}. */
  synchronized void removed(File dir, boolean deleted) {
    String k = key(dir);
    boolean changed = closed.remove(k);
    changed |= built.remove(k);
    changed |= external.remove(k);
    changed |= deleted ? removed.remove(k) : removed.add(k);
    if (changed)
      save();
  }

  /** A project folder moved: what is known about it follows it. */
  synchronized void moved(File from, File to, boolean externalLocation) {
    String a = key(from), b = to.getAbsolutePath();
    try {
      b = to.getCanonicalPath();
    } catch (IOException e) {
      // keep the absolute path
    }
    if (closed.remove(a))
      closed.add(b);
    if (built.remove(a))
      built.add(b);
    external.remove(a);
    removed.remove(b);
    if (externalLocation)
      external.add(b);
    save();
  }
}
