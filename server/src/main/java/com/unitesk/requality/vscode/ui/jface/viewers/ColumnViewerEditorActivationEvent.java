package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.EventObject;

/** {@code org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent}. */
public class ColumnViewerEditorActivationEvent extends EventObject {
  private static final long serialVersionUID = 1L;
  public static final int TRAVERSAL = 0;
  public static final int MOUSE_CLICK_SELECTION = 1;
  public static final int MOUSE_DOUBLE_CLICK_SELECTION = 2;
  public static final int KEY_PRESSED = 3;
  public static final int PROGRAMMATIC = 4;

  public int eventType;
  public Object sourceEvent;
  public char character;
  public int keyCode;
  public int stateMask;
  public boolean cancel;

  public ColumnViewerEditorActivationEvent(ViewerCell cell) {
    super(cell);
  }
}
