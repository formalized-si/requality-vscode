package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.DoubleClickEvent}. */
public class DoubleClickEvent {
  private final Viewer source;
  private final ISelection selection;

  public DoubleClickEvent(Viewer source, ISelection selection) {
    this.source = source;
    this.selection = selection;
  }

  public ISelection getSelection() {
    return selection;
  }

  public Viewer getViewer() {
    return source;
  }

  public Object getSource() {
    return source;
  }
}
