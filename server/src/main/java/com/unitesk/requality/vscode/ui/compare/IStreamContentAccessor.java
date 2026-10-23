package com.unitesk.requality.vscode.ui.compare;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.compare.IStreamContentAccessor}. */
public interface IStreamContentAccessor {
  InputStream getContents() throws CoreException;
}
