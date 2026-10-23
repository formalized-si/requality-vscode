package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;

/** {@code org.eclipse.ui.actions.ActionContext}: the selection (and input) an action group works on. */
public class ActionContext {
  private ISelection selection;
  private Object input;

  public ActionContext(ISelection selection) {
    this.selection = selection;
  }

  public ISelection getSelection() {
    return selection;
  }

  public void setSelection(ISelection selection) {
    this.selection = selection;
  }

  public Object getInput() {
    return input;
  }

  public void setInput(Object input) {
    this.input = input;
  }
}
