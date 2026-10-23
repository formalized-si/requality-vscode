package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.ui.IStorageEditorInput}. */
public interface IStorageEditorInput extends IEditorInput {
  IStorage getStorage() throws CoreException;
}
