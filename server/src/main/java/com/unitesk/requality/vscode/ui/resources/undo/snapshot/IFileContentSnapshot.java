package com.unitesk.requality.vscode.ui.resources.undo.snapshot;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** The contents of a file recorded in a snapshot. */
interface IFileContentSnapshot {
  InputStream getContents() throws CoreException;

  String getCharset();

  boolean exists();
}
