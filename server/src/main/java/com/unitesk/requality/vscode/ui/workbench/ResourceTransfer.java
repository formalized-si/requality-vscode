package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.swt.dnd.Transfer;

/** {@code org.eclipse.ui.part.ResourceTransfer}: workspace resources (IResource[]). */
public final class ResourceTransfer extends Transfer {
  private static final ResourceTransfer INSTANCE = new ResourceTransfer();

  private ResourceTransfer() {
  }

  public static ResourceTransfer getInstance() {
    return INSTANCE;
  }

  /** The type id of the transfer in a drag and drop (TransferData.type). */
  public static final int TYPEID = 4;

  @Override
  protected int[] getTypeIds() {
    return new int[] { TYPEID };
  }

  @Override
  public String[] getTypeNames() {
    return new String[] { "org.eclipse.ui.part.ResourceTransfer" };
  }
}
