package com.unitesk.requality.vscode.ui.commands;

import java.util.Map;

/**
 * The command and handler services of the workbench (implemented by the server's CommandService, which reads the
 * commands and handlers of plugin.xml).
 */
public abstract class CommandSupport {
  private static volatile CommandSupport instance;

  public static CommandSupport get() {
    CommandSupport s = instance;
    if (s == null)
      throw new IllegalStateException("Command service is not started");
    return s;
  }

  public static void set(CommandSupport s) {
    instance = s;
  }

  public abstract Command getCommand(String id);

  public abstract boolean isEnabled(String id);

  public abstract boolean isHandled(String id);

  @SuppressWarnings("rawtypes")
  public abstract Object execute(String id, Map parameters, Object trigger)
      throws ExecutionException, NotDefinedException, NotEnabledException, NotHandledException;

  public abstract IEvaluationContext currentState();

  public abstract IHandlerActivation activateHandler(String commandId, IHandler handler);

  /**
   * An activation through the handler service of a part's site: the handler is active only while the part is the
   * active part (the site's service adds an ActivePartExpression), with the source priority of the active part.
   */
  public abstract IHandlerActivation activateHandler(String commandId, IHandler handler, Object part);

  /** Removes the activations made through the site of a part (the part was disposed). */
  public abstract void deactivateHandlers(Object part);

  public abstract void deactivateHandler(IHandlerActivation activation);

  /** Handlers implementing IElementUpdater changed their labels. */
  public abstract void refreshElements(String commandId);

  /**
   * The menu element of a command as its active handler shows it (IElementUpdater.updateElement: text, checked
   * state), or null when the handler does not update elements.
   */
  public UIElement updateElement(String commandId) {
    return null;
  }
}
