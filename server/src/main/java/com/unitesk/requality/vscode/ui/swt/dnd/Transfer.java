package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.Transfer}: a data type of the clipboard and of drag and drop. */
public abstract class Transfer {
  public abstract String[] getTypeNames();

  /** {@code Transfer.getTypeIds()}: the ids of the data types (TransferData.type) of the transfer. */
  protected int[] getTypeIds() {
    return new int[0];
  }

  /** {@code Transfer.getSupportedTypes()}: the data types the transfer converts. */
  public TransferData[] getSupportedTypes() {
    int[] ids = getTypeIds();
    TransferData[] r = new TransferData[ids.length];
    for (int i = 0; i < ids.length; i++)
      r[i] = new TransferData(ids[i]);
    return r;
  }

  /** {@code Transfer.isSupportedType(TransferData)}: whether the transfer converts the data type. */
  public boolean isSupportedType(TransferData transferData) {
    if (transferData == null)
      return false;
    for (int id : getTypeIds())
      if (transferData.type == id)
        return true;
    return false;
  }
}
