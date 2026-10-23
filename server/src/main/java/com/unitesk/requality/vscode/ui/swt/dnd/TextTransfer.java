package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.TextTransfer}: plain text on the system clipboard. */
public final class TextTransfer extends Transfer {
  private static final TextTransfer INSTANCE = new TextTransfer();

  private TextTransfer() {
  }

  public static TextTransfer getInstance() {
    return INSTANCE;
  }

  /** The type id of the transfer in a drag and drop (TransferData.type). */
  public static final int TYPEID = 3;

  @Override
  protected int[] getTypeIds() {
    return new int[] { TYPEID };
  }

  @Override
  public String[] getTypeNames() {
    return new String[] { "text/plain" };
  }
}
