package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.OpenEvent}. */
public class OpenEvent {
  private final Viewer viewer;
  private final ISelection selection;

  public OpenEvent(Viewer viewer, ISelection selection) {
    this.viewer = viewer;
    this.selection = selection;
  }

  public ISelection getSelection() {
    return selection;
  }

  public Viewer getViewer() {
    return viewer;
  }
}
