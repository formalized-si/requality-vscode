package com.unitesk.requality.vscode.ui.workbench.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.resources.FileMoves;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.XMLMemento;
import com.unitesk.requality.vscode.ui.workbench.WorkbenchException;

/**
 * {@code org.eclipse.ui.internal.WorkingSetManager}: the working set manager of the workbench, persisted after each
 * change in the working sets file (the Eclipse {@code workingsets.xml} format). The file is kept next to the state file
 * of the VS Code workspace, so each workspace has its own working sets.
 */
public class WorkingSetManager extends AbstractWorkingSetManager {
  /** The name of the working sets file of the workbench (the workspace's own file name is put in front of it). */
  public static final String WORKING_SET_STATE_FILENAME = "workingsets.xml";

  private static WorkingSetManager instance;
  private static File stateFile;

  private boolean restoreInProgress;
  private boolean savePending;
  /** The name of the working set the explorer shows (kept in the file, as Eclipse keeps it in the view memento). */
  private String activeWorkingSetName;
  /** The id of the aggregate of the window's working sets (Eclipse keeps it in the window's persisted state). */
  private String windowAggregateId;
  /** The names of the working sets of the window (the working sets of the active page). */
  private String[] windowWorkingSetNames = new String[0];

  public WorkingSetManager() {
  }

  /**
   * The working set manager of the server. It reads its file when first used; a file set later replaces the manager
   * (before the explorer is used, the working sets of the workspace are read from the new file).
   */
  public static synchronized WorkingSetManager get() {
    if (instance == null) {
      instance = new WorkingSetManager();
      instance.restoreState();
    }
    return instance;
  }

  /**
   * Sets the working sets file of the workspace from the file of its state: {@code <name>-workingsets.xml} in the same
   * folder, or null (kept in memory only) when the state is kept in memory.
   */
  public static synchronized void setWorkspaceStateFile(File workspaceStateFile) {
    if (workspaceStateFile == null) {
      return;
    }
    String name = workspaceStateFile.getName();
    if (name.endsWith(".json")) {
      name = name.substring(0, name.length() - ".json".length());
    }
    stateFile = new File(workspaceStateFile.getAbsoluteFile().getParentFile(), name + "-" + WORKING_SET_STATE_FILENAME);
    instance = null;
  }

  /** The file of the working sets, or null when there is none. */
  static File getWorkingSetStateFile() {
    return stateFile;
  }

  @Override
  public void addWorkingSet(IWorkingSet workingSet) {
    super.addWorkingSet(workingSet);
    saveState();
  }

  @Override
  public void removeWorkingSet(IWorkingSet workingSet) {
    if (internalRemoveWorkingSet(workingSet)) {
      saveState();
    }
  }

  @Override
  public void workingSetChanged(IWorkingSet changedWorkingSet, String propertyChangeId, Object oldValue) {
    saveState();
    super.workingSetChanged(changedWorkingSet, propertyChangeId, oldValue);
  }

  /** The name of the active working set of the explorer, or null. */
  public synchronized String getActiveWorkingSetName() {
    return activeWorkingSetName;
  }

  /** Sets the name of the active working set of the explorer (null: none) and saves it. */
  public synchronized void setActiveWorkingSetName(String name) {
    activeWorkingSetName = name == null || name.isEmpty() ? null : name;
    saveState();
  }

  /** The id of the window's aggregate working set, or null when it has not been made yet. */
  public synchronized String getWindowAggregateId() {
    return windowAggregateId;
  }

  /** Sets the id of the window's aggregate working set and saves it. */
  public synchronized void setWindowAggregateId(String id) {
    windowAggregateId = id;
    saveState();
  }

  /** The names of the working sets of the window, in order. */
  public synchronized String[] getWindowWorkingSetNames() {
    return windowWorkingSetNames.clone();
  }

  /** Sets the names of the working sets of the window and saves them. */
  public synchronized void setWindowWorkingSetNames(String[] names) {
    windowWorkingSetNames = names == null ? new String[0] : names.clone();
    saveState();
  }

  /** Reads the file and creates the working sets stored in it. */
  public void restoreState() {
    File file = getWorkingSetStateFile();
    if (file == null || !file.exists()) {
      return;
    }
    try {
      restoreInProgress = true;
      try (FileInputStream input = new FileInputStream(file);
          BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        IMemento memento = XMLMemento.createReadRoot(reader);
        restoreWorkingSetState(memento);
        restoreMruList(memento);
        IMemento active = memento.getChild("activeWorkingSet");
        if (active != null) {
          activeWorkingSetName = active.getString("name");
        }
        IMemento window = memento.getChild("windowWorkingSets");
        if (window != null) {
          windowAggregateId = window.getString("aggregateId");
          windowWorkingSetNames = Arrays.stream(window.getChildren("workingSet")).map(IMemento::getID)
              .toArray(String[]::new);
        }
      }
    } catch (IOException | WorkbenchException e) {
      RLogger.logError(e);
    } finally {
      restoreInProgress = false;
    }
    if (savePending) {
      saveState();
      savePending = false;
    }
  }

  @Override
  protected void restoreWorkingSetState(IMemento memento) {
    super.restoreWorkingSetState(memento);
    saveState();
  }

  /** Saves the working sets in the file. */
  @Override
  protected void saveState() {
    if (restoreInProgress) {
      // bug 327396: avoid saving partial state
      savePending = true;
      return;
    }
    File file = getWorkingSetStateFile();
    if (file == null) {
      return;
    }
    try {
      writeState(file);
    } catch (IOException e) {
      RLogger.logError(e);
    }
  }

  /** Writes the state of the manager to the file (through a temporary file, so the file is never partial). */
  void writeState(File file) throws IOException {
    XMLMemento memento = XMLMemento.createWriteRoot("workingSetManager");
    saveWorkingSetState(memento);
    saveMruList(memento);
    if (activeWorkingSetName != null) {
      memento.createChild("activeWorkingSet").putString("name", activeWorkingSetName);
    }
    IMemento window = memento.createChild("windowWorkingSets");
    if (windowAggregateId != null) {
      window.putString("aggregateId", windowAggregateId);
    }
    for (String name : windowWorkingSetNames) {
      window.createChild("workingSet", name);
    }
    File dir = file.getAbsoluteFile().getParentFile();
    if (dir != null) {
      dir.mkdirs();
    }
    File tmp = new File(dir, file.getName() + ".tmp");
    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
      memento.save(writer);
    }
    FileMoves.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }
}
