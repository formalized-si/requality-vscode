package com.unitesk.requality.vscode.ui.resources;

import java.io.InputStream;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;

/** {@code org.eclipse.core.resources.IStorage}. */
public interface IStorage extends IAdaptable {
  InputStream getContents() throws CoreException;

  IPath getFullPath();

  String getName();

  boolean isReadOnly();
}
