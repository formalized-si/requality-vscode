package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.TransferData}: the data type of a drag and drop transfer. */
public class TransferData {
  /** Id of the transfer type (see {@link Transfer}); 0 when not set. */
  public int type;
  /** Format of the data (SWT: 0 for untyped data). */
  public int format;

  public TransferData() {
  }

  public TransferData(int type) {
    this.type = type;
  }
}
