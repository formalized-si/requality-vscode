package com.unitesk.requality.vscode.ui.workbench;

/**
 * {@code org.eclipse.ui.part.EditorPart}: an editor rendered by the server in a VS Code editor tab (see
 * {@link Workbench#registerEditor}).
 */
public abstract class EditorPart extends WorkbenchPart implements IEditorPart {
  private IEditorInput input;

  @Override
  public IEditorInput getEditorInput() {
    return input;
  }

  protected void setInput(IEditorInput input) {
    this.input = input;
    firePropertyChange(PROP_INPUT);
  }

  protected void setInputWithNotify(IEditorInput input) {
    setInput(input);
  }

  @Override
  public IEditorSite getEditorSite() {
    return (IEditorSite) getSite();
  }

  @Override
  public boolean isSaveOnCloseNeeded() {
    return isDirty();
  }
}
