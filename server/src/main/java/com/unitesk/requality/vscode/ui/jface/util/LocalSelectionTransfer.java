package com.unitesk.requality.vscode.ui.jface.util;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.swt.dnd.TransferData;
import com.unitesk.requality.vscode.ui.swt.dnd.Transfer;

/**
 * {@code org.eclipse.jface.util.LocalSelectionTransfer}: transfers a selection inside the same application (the
 * selection is kept by reference, as in JFace).
 */
public class LocalSelectionTransfer extends Transfer {
  private static final String TYPE_NAME = "local-selection-transfer-format";
  /** The type id of the transfer in a drag and drop (TransferData.type). */
  public static final int TYPEID = 1;
  private static final LocalSelectionTransfer INSTANCE = new LocalSelectionTransfer();

  private ISelection selection;
  private long selectionSetTime;

  private LocalSelectionTransfer() {
  }

  /** Returns the singleton instance of this transfer. */
  public static LocalSelectionTransfer getTransfer() {
    return INSTANCE;
  }

  /** Returns the selection that is currently being transferred, or null. */
  public ISelection getSelection() {
    return selection;
  }

  /** Sets the selection to be transferred. */
  public void setSelection(ISelection selection) {
    this.selection = selection;
  }

  /** Returns the time the selection was set (the time of the drag start). */
  public long getSelectionSetTime() {
    return selectionSetTime;
  }

  /** Sets the time the selection was set. */
  public void setSelectionSetTime(long time) {
    this.selectionSetTime = time;
  }

  /** Clears the transferred selection. */
  public void clearSelection() {
    selection = null;
  }

  /** Whether the given transfer data is of the local selection type. */
  public boolean isSupportedType(TransferData transferData) {
    return transferData != null && transferData.type == TYPEID;
  }

  @Override
  protected int[] getTypeIds() {
    return new int[] { TYPEID };
  }

  @Override
  public String[] getTypeNames() {
    return new String[] {TYPE_NAME};
  }
}
