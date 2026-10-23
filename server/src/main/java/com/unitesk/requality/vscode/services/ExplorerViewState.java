package com.unitesk.requality.vscode.services;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.resources.FileMoves;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.XMLMemento;

/**
 * The memento of the Requality Explorer view (what Eclipse saves in the workbench state for a view: the state of the
 * action providers of the navigator, e.g. the working set of WorkingSetActionProvider), kept next to the workspace
 * state of the server.
 */
// VSCODE: Eclipse saves the view memento when the workbench closes; VS Code may end the server at any time, so the
// explorer saves it when the state of an action provider changes.
public final class ExplorerViewState {
  private static final String TAG_VIEW = "view";
  private static File file;

  private ExplorerViewState() {
  }

  /** The file of the memento: {@code <name>-explorer.xml} next to the workspace state file (null: in memory only). */
  public static synchronized void setWorkspaceStateFile(File workspaceStateFile) {
    if (workspaceStateFile == null) {
      file = null;
      return;
    }
    String name = workspaceStateFile.getName();
    if (name.endsWith(".json"))
      name = name.substring(0, name.length() - ".json".length());
    file = new File(workspaceStateFile.getAbsoluteFile().getParentFile(), name + "-explorer.xml");
  }

  /** The saved memento, or null. */
  public static synchronized IMemento load() {
    if (file == null || !file.isFile())
      return null;
    try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      return XMLMemento.createReadRoot(r);
    } catch (Exception e) {
      RLogger.logInfo("Cannot read the explorer state " + file + ": " + e);
      return null;
    }
  }

  /** Saves the memento the action providers of the explorer fill. */
  public static synchronized void save(java.util.function.Consumer<IMemento> filler) {
    XMLMemento m = XMLMemento.createWriteRoot(TAG_VIEW);
    filler.accept(m);
    if (file == null)
      return;
    try {
      File dir = file.getAbsoluteFile().getParentFile();
      if (dir != null)
        dir.mkdirs();
      File tmp = new File(dir, file.getName() + ".tmp");
      try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
        m.save(w);
      }
      FileMoves.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      RLogger.logError(e);
    }
  }
}
