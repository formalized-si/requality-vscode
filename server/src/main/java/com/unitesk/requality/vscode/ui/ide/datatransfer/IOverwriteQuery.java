package com.unitesk.requality.vscode.ui.ide.datatransfer;

/** {@code org.eclipse.ui.dialogs.IOverwriteQuery}: asks whether an existing resource or file is overwritten. */
public interface IOverwriteQuery {
  /** Overwrite this resource. */
  String YES = "YES"; //$NON-NLS-1$
  /** Do not overwrite this resource. */
  String NO = "NO"; //$NON-NLS-1$
  /** Overwrite all remaining resources without asking. */
  String ALL = "ALL"; //$NON-NLS-1$
  /** Do not overwrite any remaining resources without asking. */
  String NO_ALL = "NO_ALL"; //$NON-NLS-1$
  /** Cancel the operation. */
  String CANCEL = "CANCEL"; //$NON-NLS-1$

  /** Returns one of the answers above for the path of the existing resource. */
  String queryOverwrite(String pathString);
}
