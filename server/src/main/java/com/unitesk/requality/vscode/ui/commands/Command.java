package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.core.commands.Command}: a command of the plug-in; execution goes through the handler service. */
public class Command {
  private final String id;
  private final String name;

  public Command(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public String getName() throws NotDefinedException {
    return name;
  }

  public boolean isDefined() {
    return true;
  }

  public boolean isEnabled() {
    return CommandSupport.get().isEnabled(id);
  }

  public boolean isHandled() {
    return CommandSupport.get().isHandled(id);
  }

  public Object executeWithChecks(ExecutionEvent event)
      throws ExecutionException, NotDefinedException, NotEnabledException, NotHandledException {
    return CommandSupport.get().execute(id, event.getParameters(), event.getTrigger());
  }

  @Override
  public String toString() {
    return "Command(" + id + ")";
  }
}
