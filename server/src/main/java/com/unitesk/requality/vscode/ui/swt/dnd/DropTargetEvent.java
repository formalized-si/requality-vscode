package com.unitesk.requality.vscode.ui.swt.dnd;

import com.unitesk.requality.vscode.ui.swt.events.TypedEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.swt.dnd.DropTargetEvent}: the drop target event delivered to a drop listener. */
public class DropTargetEvent extends TypedEvent {
  /** The data of the drop (SWT: the transferred object). */
  public Object data;
  /** The drop operation ({@link DND#DROP_COPY}, {@link DND#DROP_MOVE}, {@link DND#DROP_LINK} or none). */
  public int detail;
  /** The x and y location of the drop, relative to the target. */
  public int x;
  public int y;
  /** The drop operations the drag source allows. */
  public int operations;
  /** The data types the drag source offers. */
  public TransferData[] dataTypes;
  /** The data type that the drop target will receive. */
  public TransferData currentDataType;
  /** The feedback of the drop target (see {@link DND} FEEDBACK_ constants). */
  public int feedback;
  /** The item under the drop (a TreeItem or TableItem of the target), or null. */
  public com.unitesk.requality.vscode.ui.swt.widgets.Widget item;
  /**
   * VSCODE: the modifier keys held at the drop ({@code SWT.CTRL}, {@code SWT.SHIFT}, {@code SWT.ALT}): SWT tells a drop
   * listener about them through the key events of the display, which the webview sends with the drop.
   */
  public int stateMask;

  public DropTargetEvent(Event e) {
    super(e);
  }

  public DropTargetEvent(Object source) {
    super(source);
  }
}
