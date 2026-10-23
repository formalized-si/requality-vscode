package com.unitesk.requality.vscode.ui.commands;

import com.unitesk.requality.vscode.ui.jface.action.IAction;

/**
 * {@code org.eclipse.jface.commands.ActionHandler}: a handler running an action (the global action handlers of a
 * part's action bars become the handlers of their retargetable commands while the part is active).
 */
public final class ActionHandler extends AbstractHandler {
  private final IAction action;

  public ActionHandler(IAction action) {
    if (action == null)
      throw new NullPointerException("Cannot create an ActionHandler with a null action");
    this.action = action;
  }

  public IAction getAction() {
    return action;
  }

  @Override
  public boolean isEnabled() {
    return action.isEnabled();
  }

  @Override
  public boolean isHandled() {
    return action.isHandled();
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    if ((action.getStyle() == IAction.AS_CHECK_BOX) || (action.getStyle() == IAction.AS_RADIO_BUTTON))
      action.setChecked(!action.isChecked());
    try {
      action.runWithEvent(null);
    } catch (Exception e) {
      throw new ExecutionException("While executing the action, an exception occurred", e);
    }
    return null;
  }

  @Override
  public String toString() {
    return "ActionHandler(" + action + ")";
  }
}
