package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.DND}: drag and drop operations and feedback constants (SWT values). */
public final class DND {
  public static final int DROP_NONE = 0;
  public static final int DROP_COPY = 1;
  public static final int DROP_MOVE = 2;
  public static final int DROP_LINK = 4;
  public static final int DROP_TARGET_MOVE = 1 << 3;
  public static final int DROP_DEFAULT = 1 << 4;
  public static final int DragEnd = 2000;
  public static final int DragSetData = 2001;
  public static final int DragEnter = 2002;
  public static final int DragLeave = 2003;
  public static final int DragOver = 2004;
  public static final int DragOperationChanged = 2005;
  public static final int Drop = 2006;
  public static final int DropAccept = 2007;
  public static final int DragStart = 2008;
  /** {@code DND.DRAG_SOURCE_KEY}: the widget data key of the drag source of a control. */
  public static final String DRAG_SOURCE_KEY = "DragSource";
  /** {@code DND.DROP_TARGET_KEY}: the widget data key of the drop target of a control. */
  public static final String DROP_TARGET_KEY = "DropTarget";
  public static final int FEEDBACK_NONE = 0;
  public static final int FEEDBACK_SELECT = 1;
  public static final int FEEDBACK_INSERT_BEFORE = 2;
  public static final int FEEDBACK_INSERT_AFTER = 4;

  private DND() {
  }
}
