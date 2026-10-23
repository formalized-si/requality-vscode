package com.unitesk.requality.vscode.ui.swt.dnd;

/** {@code org.eclipse.swt.dnd.FileTransfer}: a list of file system paths. */
public final class FileTransfer extends Transfer {
  private static final FileTransfer INSTANCE = new FileTransfer();

  private FileTransfer() {
  }

  public static FileTransfer getInstance() {
    return INSTANCE;
  }

  /** The type id of file lists in a drop (TransferData.type). */
  public static final int TYPEID = 2;

  /** Whether the transfer data is a list of files. */
  public boolean isSupportedType(TransferData transferData) {
    return transferData != null && transferData.type == TYPEID;
  }

  @Override
  protected int[] getTypeIds() {
    return new int[] { TYPEID };
  }

  @Override
  public String[] getTypeNames() {
    return new String[] { "text/uri-list" };
  }
}
