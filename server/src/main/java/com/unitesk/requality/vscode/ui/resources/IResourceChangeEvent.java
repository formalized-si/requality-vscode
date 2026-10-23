package com.unitesk.requality.vscode.ui.resources;

/** {@code org.eclipse.core.resources.IResourceChangeEvent}. */
public interface IResourceChangeEvent {
  int POST_CHANGE = 1;
  int PRE_CLOSE = 2;
  int PRE_DELETE = 4;
  int PRE_BUILD = 8;
  int POST_BUILD = 16;
  int PRE_REFRESH = 32;

  int getType();

  IResourceDelta getDelta();

  IResource getResource();

  Object getSource();
}
