package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.HTMLTransfer}: copied as text (the VS Code clipboard holds text only). */
public final class HTMLTransfer extends Transfer {
  private static final HTMLTransfer INSTANCE = new HTMLTransfer();

  private HTMLTransfer() {
  }

  public static HTMLTransfer getInstance() {
    return INSTANCE;
  }

  /** The type id of the transfer in a drag and drop (TransferData.type). */
  public static final int TYPEID = 5;

  @Override
  protected int[] getTypeIds() {
    return new int[] { TYPEID };
  }

  @Override
  public String[] getTypeNames() {
    return new String[] { "text/html" };
  }
}
