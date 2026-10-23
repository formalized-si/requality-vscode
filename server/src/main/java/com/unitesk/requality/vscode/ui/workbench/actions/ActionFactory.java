package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code org.eclipse.ui.actions.ActionFactory} (and the project actions of {@code IDEActionFactory}): the ids of the
 * retargetable global actions and the commands they handle. A part sets a global action handler under the action id
 * ({@code IActionBars.setGlobalActionHandler}); while the part is active the action handles the command.
 */
public final class ActionFactory {
  private static final Map<String, String> COMMANDS = new LinkedHashMap<>();

  public static final ActionFactory COPY = new ActionFactory("copy", "org.eclipse.ui.edit.copy");
  public static final ActionFactory CUT = new ActionFactory("cut", "org.eclipse.ui.edit.cut");
  public static final ActionFactory PASTE = new ActionFactory("paste", "org.eclipse.ui.edit.paste");
  public static final ActionFactory DELETE = new ActionFactory("delete", "org.eclipse.ui.edit.delete");
  public static final ActionFactory SELECT_ALL = new ActionFactory("selectAll", "org.eclipse.ui.edit.selectAll");
  public static final ActionFactory UNDO = new ActionFactory("undo", "org.eclipse.ui.edit.undo");
  public static final ActionFactory REDO = new ActionFactory("redo", "org.eclipse.ui.edit.redo");
  public static final ActionFactory FIND = new ActionFactory("find", "org.eclipse.ui.edit.findReplace");
  public static final ActionFactory RENAME = new ActionFactory("rename", "org.eclipse.ui.edit.rename");
  public static final ActionFactory MOVE = new ActionFactory("move", "org.eclipse.ui.edit.move");
  public static final ActionFactory REFRESH = new ActionFactory("refresh", "org.eclipse.ui.file.refresh");
  public static final ActionFactory PROPERTIES = new ActionFactory("properties", "org.eclipse.ui.file.properties");
  public static final ActionFactory PRINT = new ActionFactory("print", "org.eclipse.ui.file.print");
  public static final ActionFactory IMPORT = new ActionFactory("import", "org.eclipse.ui.file.import");
  public static final ActionFactory EXPORT = new ActionFactory("export", "org.eclipse.ui.file.export");
  public static final ActionFactory NEW = new ActionFactory("new", "org.eclipse.ui.newWizard");
  public static final ActionFactory BACK = new ActionFactory("back", "org.eclipse.ui.navigate.back");
  public static final ActionFactory FORWARD = new ActionFactory("forward", "org.eclipse.ui.navigate.forward");
  // IWorkbenchActionConstants (navigate menu)
  public static final ActionFactory GO_INTO = new ActionFactory("goInto", "org.eclipse.ui.navigate.goInto");
  public static final ActionFactory UP = new ActionFactory("up", "org.eclipse.ui.navigate.up");
  public static final ActionFactory GO_TO_RESOURCE = new ActionFactory("goToResource", "org.eclipse.ui.navigate.goToResource");
  // IDEActionFactory
  public static final ActionFactory BUILD_PROJECT = new ActionFactory("buildProject", "org.eclipse.ui.project.buildProject");
  public static final ActionFactory OPEN_PROJECT = new ActionFactory("openProject", "org.eclipse.ui.project.openProject");
  public static final ActionFactory CLOSE_PROJECT = new ActionFactory("closeProject", "org.eclipse.ui.project.closeProject");
  public static final ActionFactory CLOSE_UNRELATED_PROJECTS = new ActionFactory("closeUnrelatedProjects",
      "org.eclipse.ui.project.closeUnrelatedProjects");
  public static final ActionFactory BOOKMARK = new ActionFactory("bookmark", "org.eclipse.ui.edit.addBookmark");
  public static final ActionFactory ADD_TASK = new ActionFactory("addTask", "org.eclipse.ui.edit.addTask");

  private final String id;
  private final String commandId;

  private ActionFactory(String id, String commandId) {
    this.id = id;
    this.commandId = commandId;
    COMMANDS.put(commandId, id);
  }

  public String getId() {
    return id;
  }

  public String getCommandId() {
    return commandId;
  }

  /** The global action id of a retargetable command, or null. */
  public static String actionIdOf(String commandId) {
    return COMMANDS.get(commandId);
  }

  /** Command id → global action id of every retargetable command. */
  public static Map<String, String> retargetableCommands() {
    return Collections.unmodifiableMap(COMMANDS);
  }
}
