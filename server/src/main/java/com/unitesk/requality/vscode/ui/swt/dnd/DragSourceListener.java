package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.DragSourceListener}. */
public interface DragSourceListener {
  void dragStart(DragSourceEvent event);

  void dragSetData(DragSourceEvent event);

  void dragFinished(DragSourceEvent event);
}
