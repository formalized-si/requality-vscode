package com.unitesk.requality.vscode.ui.swt.dnd;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.swt.dnd.DragSource}: makes a control the source of a drag and drop. The listeners are told when the
 * drag starts, provide the data of the transfer the drop target asks for, and are told how the drag finished.
 * VSCODE: the control is draggable in the webview; the drag carries the id of the control, and the drop target asks
 * the source for the data on the server (a drop on a control of a server shell: see {@link DropTarget}).
 */
public class DragSource {
  private final Control control;
  private final int style;
  private Transfer[] transfers = new Transfer[0];
  private final List<DragSourceListener> listeners = new CopyOnWriteArrayList<>();
  private boolean started;
  private boolean disposed;

  public DragSource(Control control, int style) {
    this.control = control;
    this.style = style;
    control.setData(DND.DRAG_SOURCE_KEY, this);
    control.setDndRole("dragSource", true);
    control.addDisposeListener(e -> dispose());
  }

  public Control getControl() {
    return control;
  }

  /** The drop operations the source allows (the style of the drag source). */
  public int getStyle() {
    return style;
  }

  public void setTransfer(Transfer... transferAgents) {
    transfers = transferAgents == null ? new Transfer[0] : transferAgents;
  }

  public Transfer[] getTransfer() {
    return transfers;
  }

  public void addDragListener(DragSourceListener listener) {
    listeners.add(listener);
  }

  public void removeDragListener(DragSourceListener listener) {
    listeners.remove(listener);
  }

  public DragSourceListener[] getDragListeners() {
    return listeners.toArray(new DragSourceListener[0]);
  }

  public void dispose() {
    if (disposed)
      return;
    disposed = true;
    if (!control.isDisposed()) {
      control.setData(DND.DRAG_SOURCE_KEY, null);
      control.setDndRole("dragSource", false);
    }
  }

  public boolean isDisposed() {
    return disposed;
  }

  /** The data types of the transfers of the source. */
  TransferData[] dataTypes() {
    List<TransferData> r = new java.util.ArrayList<>();
    for (Transfer t : transfers)
      r.addAll(java.util.Arrays.asList(t.getSupportedTypes()));
    return r.toArray(new TransferData[0]);
  }

  /** The drag started in the webview: dragStart (a listener may refuse the drag with doit = false). */
  void dragStarted(JsonObject d) {
    DragSourceEvent e = new DragSourceEvent(control);
    e.doit = true;
    for (DragSourceListener l : listeners)
      l.dragStart(e);
    started = e.doit;
  }

  /** Whether a drag of the source is in progress (started and not refused). */
  boolean isDragging() {
    return started;
  }

  /** dragSetData for the data type the drop target asks for; null when the source gives no data. */
  Object dataFor(TransferData type) {
    DragSourceEvent e = new DragSourceEvent(control);
    e.dataType = type;
    e.doit = true;
    for (DragSourceListener l : listeners)
      l.dragSetData(e);
    return e.doit ? e.data : null;
  }

  /** The drag ended in the webview: dragFinished with the operation of the drop (none when it was not dropped). */
  void dragEnded(JsonObject d) {
    if (!started)
      return;
    started = false;
    String effect = d.has("effect") && !d.get("effect").isJsonNull() ? d.get("effect").getAsString() : "none";
    DragSourceEvent e = new DragSourceEvent(control);
    e.detail = "move".equals(effect) ? DND.DROP_MOVE : "copy".equals(effect) ? DND.DROP_COPY
        : "link".equals(effect) ? DND.DROP_LINK : DND.DROP_NONE;
    e.doit = e.detail != DND.DROP_NONE;
    for (DragSourceListener l : listeners)
      l.dragFinished(e);
  }
}
