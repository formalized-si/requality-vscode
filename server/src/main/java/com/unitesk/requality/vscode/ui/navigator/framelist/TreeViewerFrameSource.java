package com.unitesk.requality.vscode.ui.navigator.framelist;

import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.AbstractTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;

/**
 * {@code org.eclipse.ui.internal.navigator.framelist.TreeViewerFrameSource}: frames of a tree viewer; a frame change
 * sets the viewer's input, expanded elements and selection.
 */
public class TreeViewerFrameSource implements IFrameSource {
  private final AbstractTreeViewer viewer;

  public TreeViewerFrameSource(AbstractTreeViewer viewer) {
    this.viewer = viewer;
  }

  public void connectTo(FrameList frameList) {
    frameList.addPropertyChangeListener(this::handlePropertyChange);
  }

  protected TreeFrame createFrame(Object input) {
    return new TreeFrame(viewer, input);
  }

  protected void frameChanged(TreeFrame frame) {
    viewer.getControl().setRedraw(false);
    viewer.setInput(frame.getInput());
    if (frame.getExpandedElements() != null)
      viewer.setExpandedElements(frame.getExpandedElements());
    viewer.setSelection(frame.getSelection(), true);
    viewer.getControl().setRedraw(true);
  }

  protected Frame getCurrentFrame(int flags) {
    Object input = viewer.getInput();
    TreeFrame frame = createFrame(input);
    if ((flags & IFrameSource.FULL_CONTEXT) != 0) {
      frame.setSelection(viewer.getSelection());
      frame.setExpandedElements(viewer.getExpandedElements());
    }
    return frame;
  }

  @Override
  public Frame getFrame(int whichFrame, int flags) {
    switch (whichFrame) {
      case IFrameSource.CURRENT_FRAME:
        return getCurrentFrame(flags);
      case IFrameSource.PARENT_FRAME:
        return getParentFrame(flags);
      case IFrameSource.SELECTION_FRAME:
        return getSelectionFrame(flags);
      default:
        return null;
    }
  }

  protected Frame getParentFrame(int flags) {
    Object input = viewer.getInput();
    ITreeContentProvider provider = (ITreeContentProvider) viewer.getContentProvider();
    Object parent = provider.getParent(input);
    if (parent == null)
      return null;
    TreeFrame frame = createFrame(parent);
    if ((flags & IFrameSource.FULL_CONTEXT) != 0) {
      frame.setSelection(viewer.getSelection());
      Object[] expanded = viewer.getExpandedElements();
      Object[] newExpanded = new Object[expanded.length + 1];
      System.arraycopy(expanded, 0, newExpanded, 0, expanded.length);
      newExpanded[newExpanded.length - 1] = input;
      frame.setExpandedElements(newExpanded);
    }
    return frame;
  }

  protected Frame getSelectionFrame(int flags) {
    IStructuredSelection sel = viewer.getStructuredSelection();
    if (sel.size() == 1) {
      Object o = sel.getFirstElement();
      if (viewer.isExpandable(o)) {
        TreeFrame frame = createFrame(o);
        if ((flags & IFrameSource.FULL_CONTEXT) != 0) {
          frame.setSelection(viewer.getSelection());
          frame.setExpandedElements(viewer.getExpandedElements());
        }
        return frame;
      }
    }
    return null;
  }

  public AbstractTreeViewer getViewer() {
    return viewer;
  }

  protected void handlePropertyChange(PropertyChangeEvent event) {
    if (FrameList.P_CURRENT_FRAME.equals(event.getProperty()))
      frameChanged((TreeFrame) event.getNewValue());
  }
}
