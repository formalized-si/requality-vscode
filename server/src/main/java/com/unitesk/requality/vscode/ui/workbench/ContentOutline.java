package com.unitesk.requality.vscode.ui.workbench;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.ui.views.contentoutline.ContentOutline}: the Outline view, a page book showing the
 * {@link IContentOutlinePage} of the active editor (or "An outline is not available.").
 */
public class ContentOutline extends ViewPart implements IPartListener, ISelectionProvider {
  public static final String ID = "org.eclipse.ui.views.ContentOutline";
  private Composite book;
  private Label defaultPage;
  private final Map<IEditorPart, IContentOutlinePage> pages = new HashMap<>();
  private IContentOutlinePage current;
  private final java.util.List<ISelectionChangedListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
  private final ISelectionChangedListener forward = event -> {
    SelectionChangedEvent e = new SelectionChangedEvent(this, event.getSelection());
    for (ISelectionChangedListener l : listeners)
      l.selectionChanged(e);
  };

  @Override
  public void createPartControl(Composite parent) {
    book = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout();
    gl.marginWidth = 0;
    gl.marginHeight = 0;
    book.setLayout(gl);
    defaultPage = new Label(book, SWT.LEFT | SWT.TOP | SWT.WRAP);
    defaultPage.setText("An outline is not available.");
    defaultPage.setLayoutData(new GridData(GridData.FILL_BOTH));
    if (getSite() != null)
      getSite().setSelectionProvider(this);
    Workbench.PAGE.addPartListener(this);
    IEditorPart active = Workbench.PAGE.getActiveEditor();
    if (active != null)
      partActivated(active);
  }

  private void show(Control c) {
    for (Control child : book.getChildren()) {
      boolean on = child == c;
      Object ld = child.getLayoutData();
      if (ld instanceof GridData)
        ((GridData) ld).exclude = !on;
      child.setVisible(on);
    }
    book.layout();
  }

  @Override
  public void partActivated(IWorkbenchPart part) {
    if (!(part instanceof IEditorPart) || book == null || book.isDisposed())
      return;
    IEditorPart editor = (IEditorPart) part;
    IContentOutlinePage page = pages.get(editor);
    if (page == null && !pages.containsKey(editor)) {
      Object a = editor.getAdapter(IContentOutlinePage.class);
      page = a instanceof IContentOutlinePage ? (IContentOutlinePage) a : null;
      pages.put(editor, page);
      if (page != null) {
        try {
          page.init(new PageSite(getSite()));
          page.createControl(book);
          Control c = page.getControl();
          if (c != null && c.getLayoutData() == null)
            c.setLayoutData(new GridData(GridData.FILL_BOTH));
        } catch (PartInitException | RuntimeException e) {
          RLogger.logError(e);
          pages.put(editor, null);
          page = null;
        }
      }
    }
    if (current != null)
      current.removeSelectionChangedListener(forward);
    current = page;
    if (page != null && page.getControl() != null) {
      page.addSelectionChangedListener(forward);
      show(page.getControl());
    } else {
      show(defaultPage);
    }
  }

  /** {@code PageBookView.getCurrentPage()}: the outline page of the active editor, or null. */
  public IContentOutlinePage getCurrentPage() {
    return current;
  }

  @Override
  public void partBroughtToTop(IWorkbenchPart part) {
    partActivated(part);
  }

  @Override
  public void partClosed(IWorkbenchPart part) {
    IContentOutlinePage page = pages.remove(part);
    if (page != null) {
      if (page == current) {
        current.removeSelectionChangedListener(forward);
        current = null;
        if (book != null && !book.isDisposed())
          show(defaultPage);
      }
      page.dispose();
      if (page.getControl() != null && !page.getControl().isDisposed())
        page.getControl().dispose();
    }
  }

  @Override
  public void partDeactivated(IWorkbenchPart part) {
  }

  @Override
  public void partOpened(IWorkbenchPart part) {
  }

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    listeners.remove(listener);
  }

  @Override
  public ISelection getSelection() {
    return current != null ? current.getSelection() : StructuredSelection.EMPTY;
  }

  @Override
  public void setSelection(ISelection selection) {
    if (current != null)
      current.setSelection(selection);
  }

  @Override
  public void setFocus() {
    if (current != null)
      current.setFocus();
  }

  @Override
  public void dispose() {
    Workbench.PAGE.removePartListener(this);
    for (IContentOutlinePage p : pages.values())
      if (p != null)
        p.dispose();
    pages.clear();
    super.dispose();
  }
}
