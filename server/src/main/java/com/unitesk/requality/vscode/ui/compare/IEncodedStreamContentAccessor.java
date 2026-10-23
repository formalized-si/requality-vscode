package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.compare.IEncodedStreamContentAccessor}: contents with their character encoding. */
public interface IEncodedStreamContentAccessor extends IStreamContentAccessor {
  String getCharset() throws CoreException;
}
