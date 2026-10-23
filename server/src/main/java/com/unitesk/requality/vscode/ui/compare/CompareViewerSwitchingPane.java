package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.compare.CompareViewerSwitchingPane}: a compare viewer pane whose viewer is chosen for its input
 * ({@link #getViewer(Viewer, Object)}); the viewer is replaced when the input needs another one, and the title of the
 * pane is the title of the viewer.
 */
public abstract class CompareViewerSwitchingPane extends CompareViewerPane {
  private Viewer fViewer;
  private boolean fControlVisibility;
  private String fTitle;
  private String fTitleArgument;

  public CompareViewerSwitchingPane(Composite parent, int style) {
    this(parent, style, false);
  }

  public CompareViewerSwitchingPane(Composite parent, int style, boolean visibility) {
    super(parent, style);
    fControlVisibility = visibility;
  }

  public Viewer getViewer() {
    return fViewer;
  }

  private void setViewer(Viewer newViewer) {
    if (newViewer == fViewer)
      return;
    Control oldContent = getContent();
    if (fViewer != null) {
      fViewer.removeSelectionChangedListener(this);
      if (fViewer instanceof StructuredViewer) {
        ((StructuredViewer) fViewer).removeDoubleClickListener(this);
        ((StructuredViewer) fViewer).removeOpenListener(this);
      }
      if (oldContent != null && !oldContent.isDisposed())
        oldContent.dispose();
    }
    fViewer = newViewer;
    Control content = null;
    if (fViewer != null) {
      content = fViewer.getControl();
      fViewer.addSelectionChangedListener(this);
      if (fViewer instanceof StructuredViewer) {
        ((StructuredViewer) fViewer).addDoubleClickListener(this);
        ((StructuredViewer) fViewer).addOpenListener(this);
      }
    }
    setContent(content);
    if (fControlVisibility)
      setVisible(content != null);
  }

  /** The title of the pane: the given one, else the title of the viewer, with the title argument. */
  private void updateTitle() {
    String title = fTitle;
    if (title == null && fViewer != null) {
      Object t = fViewer.getData(CompareUI.COMPARE_VIEWER_TITLE);
      if (t == null && fViewer.getControl() != null)
        t = fViewer.getControl().getData(CompareUI.COMPARE_VIEWER_TITLE);
      if (t instanceof String)
        title = (String) t;
    }
    if (title == null)
      title = "";
    if (fTitleArgument != null)
      title = title + " (" + fTitleArgument + ")";
    setText(title);
  }

  public void setTitleArgument(String argument) {
    fTitleArgument = argument;
    updateTitle();
  }

  public void setText(String label, boolean fixed) {
    if (fixed)
      fTitle = label;
    setText(label);
  }

  @Override
  public void setInput(Object input) {
    if (!fControlVisibility || input != null || getInput() != null) {
      Viewer newViewer = getViewer(fViewer, input);
      setViewer(newViewer);
    }
    if (fViewer != null)
      fViewer.setInput(input);
    super.setInput(input);
    updateTitle();
    if (fControlVisibility && fViewer == null)
      setVisible(false);
  }

  /** Whether no viewer shows the input. */
  public boolean isEmpty() {
    return fViewer == null;
  }

  @Override
  public ISelection getSelection() {
    return fViewer != null ? fViewer.getSelection() : super.getSelection();
  }

  @Override
  public void setSelection(ISelection s) {
    if (fViewer != null)
      fViewer.setSelection(s);
  }

  @Override
  public boolean setFocus() {
    Control c = getContent();
    return c != null && !c.isDisposed() ? c.setFocus() : super.setFocus();
  }

  /** The viewer for the input (the old viewer when it fits), or null. */
  protected abstract Viewer getViewer(Viewer oldViewer, Object input);

  static int style() {
    return SWT.BORDER | SWT.FLAT;
  }
}
