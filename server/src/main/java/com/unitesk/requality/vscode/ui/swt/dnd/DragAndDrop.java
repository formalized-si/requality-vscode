package com.unitesk.requality.vscode.ui.swt.dnd;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/** VSCODE: delivers the drag and drop events of the webview to the drag source and the drop target of a control. */
public final class DragAndDrop {
  private DragAndDrop() {
  }

  /** Whether the event is a drag and drop event of the control (then handled). */
  public static boolean dispatch(Control control, String type, JsonObject data) {
    switch (type) {
      case "dragstart":
      case "dragend": {
        Object s = control.getData(DND.DRAG_SOURCE_KEY);
        if (s instanceof DragSource source) {
          if ("dragstart".equals(type))
            source.dragStarted(data);
          else
            source.dragEnded(data);
        }
        return true;
      }
      case "drop": {
        Object t = control.getData(DND.DROP_TARGET_KEY);
        if (t instanceof DropTarget target)
          target.dropped(data);
        return true;
      }
      default:
        return false;
    }
  }
}
