package com.unitesk.requality.vscode.ui.compare.contentmergeviewer;

import java.util.ResourceBundle;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.viewers.ContentViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.graphics.Rectangle;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.compare.contentmergeviewer.ContentMergeViewer}: a two-way (optionally three-way) side by side
 * compare viewer. Subclasses create their panes in {@link #createControls(Composite)}, place them in
 * {@link #handleResizeAncestor} and {@link #handleResizeLeftRight} and show the contents in {@link #updateContent}.
 */
public abstract class ContentMergeViewer extends ContentViewer {
  private final int fStyle;
  private final ResourceBundle fBundle;
  private final CompareConfiguration fCompareConfiguration;
  private Composite fControl;
  private boolean fLeftDirty;
  private boolean fRightDirty;
  private boolean fAncestorVisible;

  public ContentMergeViewer(int style, ResourceBundle bundle, CompareConfiguration configuration) {
    fStyle = style;
    fBundle = bundle;
    fCompareConfiguration = configuration;
  }

  /** Creates the composite of the viewer, the panes (createControls) and lays them out. */
  protected void buildControl(Composite parent) {
    fControl = new Composite(parent, fStyle);
    createControls(fControl);
    layoutPanes();
  }

  @Override
  public Control getControl() {
    return fControl;
  }

  public CompareConfiguration getCompareConfiguration() {
    return fCompareConfiguration;
  }

  protected ResourceBundle getResourceBundle() {
    return fBundle;
  }

  public String getTitle() {
    return "";
  }

  public boolean isLeftDirty() {
    return fLeftDirty;
  }

  protected void setLeftDirty(boolean dirty) {
    fLeftDirty = dirty;
  }

  public boolean isRightDirty() {
    return fRightDirty;
  }

  protected void setRightDirty(boolean dirty) {
    fRightDirty = dirty;
  }

  @Override
  public void refresh() {
    if (fControl != null && !fControl.isDisposed())
      inputChanged(getInput(), null);
  }

  @Override
  public ISelection getSelection() {
    return new StructuredSelection();
  }

  /** Selection is not supported by compare viewers. */
  @Override
  public void setSelection(ISelection selection, boolean reveal) {
  }

  /** Shows the ancestor, left and right sides of an {@link ICompareInput} (null sides otherwise). */
  @Override
  protected void inputChanged(Object input, Object oldInput) {
    Object ancestor = null, left = null, right = null;
    if (input instanceof ICompareInput) {
      ICompareInput ci = (ICompareInput) input;
      ancestor = ci.getAncestor();
      left = ci.getLeft();
      right = ci.getRight();
    }
    fAncestorVisible = ancestor != null;
    updateContent(ancestor, left, right);
    layoutPanes();
  }

  /**
   * ContentMergeViewerLayout: the ancestor pane on top (when visible), the left and right panes side by side. The
   * subclass places its panes with handleResizeAncestor / handleResizeLeftRight; the renderer has no absolute
   * placement, so the bounds they set (for a nominal viewer size when the real one is unknown) are turned into a
   * two-column grid: a pane as wide as the viewer spans both columns, a hidden or empty pane is excluded.
   */
  private void layoutPanes() {
    if (fControl == null || fControl.isDisposed())
      return;
    Point size = fControl.getSize();
    int width = size.x > 0 ? size.x : 1000, height = size.y > 0 ? size.y : 600;
    int ancestorHeight = fAncestorVisible ? height / 3 : 0;
    handleResizeAncestor(0, 0, fAncestorVisible ? width : 0, ancestorHeight);
    int leftWidth = width / 2;
    handleResizeLeftRight(0, ancestorHeight, leftWidth, 0, width - leftWidth, height - ancestorHeight);
    GridLayout gl = new GridLayout(2, true);
    gl.marginWidth = gl.marginHeight = 0;
    fControl.setLayout(gl);
    for (Control c : fControl.getChildren()) {
      Rectangle b = c.getBounds();
      GridData gd = new GridData(SWT.FILL, SWT.FILL, true, b.y >= ancestorHeight || !fAncestorVisible);
      gd.exclude = !c.getVisible() || b.width <= 0;
      if (b.width >= width)
        gd.horizontalSpan = 2;
      c.setLayoutData(gd);
    }
    fControl.layout(true, true);
  }

  /** Creates the panes of the viewer in the given composite. */
  protected abstract void createControls(Composite composite);

  protected abstract void handleResizeAncestor(int x, int y, int width, int height);

  protected abstract void handleResizeLeftRight(int x, int y, int leftWidth, int centerWidth, int rightWidth,
      int height);

  protected abstract void updateContent(Object ancestor, Object left, Object right);

  protected abstract void copy(boolean leftToRight);

  protected abstract byte[] getContents(boolean left);

  /** Releases the viewer's resources when its control is disposed. */
  protected void handleDispose(DisposeEvent event) {
  }
}
