package com.unitesk.requality.vscode.ui.workbench;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.ui.views.contentoutline.ContentOutlinePage}: a page showing a tree of the content of the active
 * editor. Its selection provider forwards the selection and the listeners of the tree viewer.
 */
public class ContentOutlinePage extends Page implements IContentOutlinePage, ISelectionChangedListener {
  private TreeViewer treeViewer;
  private final List<ISelectionChangedListener> selectionListeners = new CopyOnWriteArrayList<>();

  /** {@code ContentOutlinePage#createControl(Composite)}: creates the tree viewer of the outline. */
  @Override
  public void createControl(Composite parent) {
    treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    treeViewer.addSelectionChangedListener(this);
    getSite().setSelectionProvider(this);
  }

  /** {@code ContentOutlinePage#getTreeViewer()}: the tree viewer, null until {@link #createControl} is called. */
  protected TreeViewer getTreeViewer() {
    return treeViewer;
  }

  @Override
  public Control getControl() {
    return treeViewer == null ? null : treeViewer.getControl();
  }

  @Override
  public void setFocus() {
    if (treeViewer != null)
      treeViewer.getControl().setFocus();
  }

  @Override
  public void dispose() {
    if (treeViewer != null)
      treeViewer.removeSelectionChangedListener(this);
    selectionListeners.clear();
    super.dispose();
  }

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener listener) {
    selectionListeners.add(listener);
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    selectionListeners.remove(listener);
  }

  @Override
  public ISelection getSelection() {
    return treeViewer == null ? StructuredSelection.EMPTY : treeViewer.getSelection();
  }

  @Override
  public void setSelection(ISelection selection) {
    if (treeViewer != null)
      treeViewer.setSelection(selection);
  }

  /** Forwards the selection of the tree viewer to the listeners of this page. */
  @Override
  public void selectionChanged(SelectionChangedEvent event) {
    for (ISelectionChangedListener l : selectionListeners)
      l.selectionChanged(event);
  }
}
