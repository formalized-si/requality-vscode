package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.EventObject;

/** {@code org.eclipse.jface.viewers.ColumnViewerEditorDeactivationEvent}. */
public class ColumnViewerEditorDeactivationEvent extends EventObject {
  private static final long serialVersionUID = 1L;
  public static final int EDITOR_CANCELED = 0;
  public static final int EDITOR_SAVED = 1;
  public int eventType;

  public ColumnViewerEditorDeactivationEvent(Object source) {
    super(source);
  }
}
