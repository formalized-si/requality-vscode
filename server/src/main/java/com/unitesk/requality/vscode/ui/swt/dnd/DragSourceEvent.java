package com.unitesk.requality.vscode.ui.swt.dnd;

import com.unitesk.requality.vscode.ui.swt.events.TypedEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.dnd.DragSourceEvent}: the event of a drag source (start, data request, end of the drag). */
public class DragSourceEvent extends TypedEvent {
  /** The operation of the drop that ended the drag ({@link DND#DROP_COPY}, ..., {@link DND#DROP_NONE}). */
  public int detail;
  /** Whether the drag may start (dragStart) or the data is valid (dragSetData). */
  public boolean doit = true;
  /** The location of the drag start, relative to the control. */
  public int x;
  public int y;
  /** The type of the data requested by dragSetData. */
  public TransferData dataType;
  /** The image of the drag (not shown by the webview). */
  public Object image;
  public int offsetX;
  public int offsetY;

  public DragSourceEvent(Event e) {
    super(e);
  }

  public DragSourceEvent(Object source) {
    super(source);
  }
}
