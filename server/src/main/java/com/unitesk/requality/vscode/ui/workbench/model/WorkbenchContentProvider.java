package com.unitesk.requality.vscode.ui.workbench.model;

import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeEvent;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeListener;
import com.unitesk.requality.vscode.ui.resources.IResourceDelta;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * {@code org.eclipse.ui.model.WorkbenchContentProvider}: listens to the workspace while it shows an input and passes
 * the resource deltas to {@link #processDelta(IResourceDelta)}.
 */
public class WorkbenchContentProvider extends BaseWorkbenchContentProvider implements IResourceChangeListener {
  private Viewer viewer;
  private boolean listening;

  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    super.inputChanged(viewer, oldInput, newInput);
    this.viewer = viewer;
    if (!listening) {
      ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
      listening = true;
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    if (listening) {
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
      listening = false;
    }
  }

  @Override
  public void resourceChanged(IResourceChangeEvent event) {
    IResourceDelta delta = event.getDelta();
    if (delta != null)
      processDelta(delta);
  }

  /** Refreshes the changed resource of the delta in the viewer (on the UI thread). */
  protected void processDelta(final IResourceDelta delta) {
    Display.getDefault().asyncExec(() -> {
      if (viewer instanceof StructuredViewer && viewer.getControl() != null && !viewer.getControl().isDisposed())
        ((StructuredViewer) viewer).refresh(delta.getResource());
    });
  }
}
