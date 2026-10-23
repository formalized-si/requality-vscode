package com.unitesk.requality.vscode.ui.compare;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.IDoubleClickListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IOpenListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.OpenEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.compare.CompareViewerPane}: a pane of a compare editor with a title bar (title and toolbar) and
 * a content control. It forwards the selection, double-click and open events of its viewer.
 * VSCODE: the SWT ViewForm is a composite with a header row (title label, toolbar) above the content.
 */
public class CompareViewerPane extends Composite implements ISelectionProvider, IDoubleClickListener,
    ISelectionChangedListener, IOpenListener {
  private final Label fTitle;
  private final Composite fHeader;
  private ToolBarManager fToolBarManager;
  private Control fContent;
  private Object fInput;
  private final List<ISelectionChangedListener> fSelectionListeners = new ArrayList<>();
  private final List<IDoubleClickListener> fDoubleClickListener = new ArrayList<>();
  private final List<IOpenListener> fOpenListener = new ArrayList<>();

  public CompareViewerPane(Composite container, int style) {
    super(container, style);
    GridLayout gl = new GridLayout(1, false);
    gl.marginWidth = gl.marginHeight = 0;
    gl.verticalSpacing = 2;
    setLayout(gl);
    fHeader = new Composite(this, SWT.NONE);
    GridLayout hl = new GridLayout(2, false);
    hl.marginWidth = hl.marginHeight = 0;
    fHeader.setLayout(hl);
    fHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    fTitle = new Label(fHeader, SWT.NONE);
    fTitle.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
  }

  /** The title of the pane. */
  public void setText(String label) {
    if (!fTitle.isDisposed())
      fTitle.setText(label == null ? "" : label);
  }

  public String getText() {
    return fTitle.getText();
  }

  public void setImage(Image image) {
    if (!fTitle.isDisposed())
      fTitle.setImage(image);
  }

  /** {@code ViewForm.setContent}: the control shown below the title bar. */
  public void setContent(Control content) {
    fContent = content;
    if (content != null && !content.isDisposed())
      content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    layout(true, true);
  }

  public Control getContent() {
    return fContent;
  }

  /** The toolbar of the pane (created on demand), or null when the composite is not a compare viewer pane. */
  public static ToolBarManager getToolBarManager(Composite parent) {
    if (parent instanceof CompareViewerPane)
      return ((CompareViewerPane) parent).getToolBarManager();
    return null;
  }

  public static void clearToolBar(Composite parent) {
    ToolBarManager tbm = getToolBarManager(parent);
    if (tbm != null) {
      tbm.removeAll();
      tbm.update(true);
    }
  }

  private ToolBarManager getToolBarManager() {
    if (fToolBarManager == null) {
      fToolBarManager = new ToolBarManager(SWT.FLAT);
      fToolBarManager.createControl(fHeader).setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
    }
    return fToolBarManager;
  }

  public Object getInput() {
    return fInput;
  }

  public void setInput(Object input) {
    if (fInput != input)
      fInput = input;
  }

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener l) {
    fSelectionListeners.add(l);
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener l) {
    fSelectionListeners.remove(l);
  }

  @Override
  public ISelection getSelection() {
    return null;
  }

  @Override
  public void setSelection(ISelection s) {
  }

  @Override
  public void selectionChanged(SelectionChangedEvent ev) {
    for (ISelectionChangedListener l : new ArrayList<>(fSelectionListeners))
      l.selectionChanged(ev);
  }

  @Override
  public void doubleClick(DoubleClickEvent event) {
    for (IDoubleClickListener l : new ArrayList<>(fDoubleClickListener))
      l.doubleClick(event);
  }

  public void addDoubleClickListener(IDoubleClickListener listener) {
    fDoubleClickListener.add(listener);
  }

  public void removeDoubleClickListener(IDoubleClickListener listener) {
    fDoubleClickListener.remove(listener);
  }

  public void addOpenListener(IOpenListener listener) {
    fOpenListener.add(listener);
  }

  public void removeOpenListener(IOpenListener listener) {
    fOpenListener.remove(listener);
  }

  @Override
  public void open(OpenEvent event) {
    for (IOpenListener l : new ArrayList<>(fOpenListener))
      l.open(event);
  }
}
