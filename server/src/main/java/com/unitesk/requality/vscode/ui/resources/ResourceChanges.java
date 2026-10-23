package com.unitesk.requality.vscode.ui.resources;

import com.unitesk.requality.vscode.ui.runtime.IPath;

/**
 * The change notification of the workspace (the POST_CHANGE event of {@code IResourceChangeListener}): the operations of
 * this layer (delete, move, open, close of resources) report their change, so the listeners (the working set updater,
 * the navigator) follow it. VSCODE: a single event per operation; the file watcher of the extension is not reported here.
 */
final class ResourceChanges {
  private ResourceChanges() {
  }

  /** Reports the change of the resource (one of the kinds of IResourceDelta) with its flags and move destination. */
  static void post(IResource resource, int kind, int flags, IPath movedTo) {
    ResourceDelta root = new ResourceDelta(WorkspaceImpl.INSTANCE.getRoot(), IResourceDelta.NO_CHANGE, 0, null);
    root.add(new ResourceDelta(resource, kind, flags, movedTo));
    WorkspaceImpl.INSTANCE.fire(new IResourceChangeEvent() {
      @Override
      public int getType() {
        return IResourceChangeEvent.POST_CHANGE;
      }

      @Override
      public IResourceDelta getDelta() {
        return root;
      }

      @Override
      public IResource getResource() {
        return null;
      }

      @Override
      public Object getSource() {
        return WorkspaceImpl.INSTANCE;
      }
    });
  }
}
