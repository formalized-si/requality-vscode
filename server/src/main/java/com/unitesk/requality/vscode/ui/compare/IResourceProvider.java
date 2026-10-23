package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.resources.IResource;

/** {@code org.eclipse.compare.IResourceProvider}: the workspace resource of a compare element. */
public interface IResourceProvider {
  IResource getResource();
}
