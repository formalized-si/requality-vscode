package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.DropTargetListener}. */
public interface DropTargetListener {
  void dragEnter(DropTargetEvent event);

  void dragLeave(DropTargetEvent event);

  void dragOperationChanged(DropTargetEvent event);

  void dragOver(DropTargetEvent event);

  void drop(DropTargetEvent event);

  void dropAccept(DropTargetEvent event);
}
