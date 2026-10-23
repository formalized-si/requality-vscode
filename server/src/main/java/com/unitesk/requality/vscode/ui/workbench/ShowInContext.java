package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;

/** {@code org.eclipse.ui.part.ShowInContext}. */
public class ShowInContext {
  private Object input;
  private ISelection selection;

  public ShowInContext(Object input, ISelection selection) {
    this.input = input;
    this.selection = selection;
  }

  public Object getInput() {
    return input;
  }

  public void setInput(Object input) {
    this.input = input;
  }

  public ISelection getSelection() {
    return selection;
  }

  public void setSelection(ISelection selection) {
    this.selection = selection;
  }
}
