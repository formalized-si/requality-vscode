package com.unitesk.requality.vscode.ui.navigator.framelist;

import com.unitesk.requality.vscode.ui.jface.viewers.AbstractTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;

/**
 * {@code org.eclipse.ui.internal.navigator.framelist.TreeFrame}: a frame of a tree viewer: its input, selection and
 * expanded elements.
 */
// saveState/restoreState of Eclipse are left out: no navigator persists its frames (CommonNavigator, RequalityCNF).
public class TreeFrame extends Frame {
  private final AbstractTreeViewer viewer;
  private Object input;
  private ISelection selection;
  private Object[] expandedElements;

  public TreeFrame(AbstractTreeViewer viewer) {
    this.viewer = viewer;
  }

  public TreeFrame(AbstractTreeViewer viewer, Object input) {
    this(viewer);
    setInput(input);
    ILabelProvider provider = (ILabelProvider) viewer.getLabelProvider();
    String name = provider == null ? null : provider.getText(input);
    if (name == null)
      name = "";
    setName(name);
    setToolTipText(name);
  }

  public Object[] getExpandedElements() {
    return expandedElements;
  }

  public Object getInput() {
    return input;
  }

  public ISelection getSelection() {
    return selection;
  }

  public AbstractTreeViewer getViewer() {
    return viewer;
  }

  public void setInput(Object input) {
    this.input = input;
  }

  public void setExpandedElements(Object[] expandedElements) {
    this.expandedElements = expandedElements;
  }

  public void setSelection(ISelection selection) {
    this.selection = selection;
  }
}
