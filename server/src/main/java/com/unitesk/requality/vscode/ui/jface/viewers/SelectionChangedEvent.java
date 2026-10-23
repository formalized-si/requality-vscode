package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.SelectionChangedEvent}. */
public class SelectionChangedEvent {
  private final ISelectionProvider source;
  private final ISelection selection;

  public SelectionChangedEvent(ISelectionProvider source, ISelection selection) {
    this.source = source;
    this.selection = selection;
  }

  public ISelection getSelection() {
    return selection;
  }

  public IStructuredSelection getStructuredSelection() {
    return selection instanceof IStructuredSelection ? (IStructuredSelection) selection : StructuredSelection.EMPTY;
  }

  public ISelectionProvider getSelectionProvider() {
    return source;
  }

  public Object getSource() {
    return source;
  }
}
