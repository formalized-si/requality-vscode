package com.unitesk.requality.vscode.ui.team.ui;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareEditorInput;
import com.unitesk.requality.vscode.ui.compare.CompareViewerPane;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.OpenEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IPage;

/**
 * {@code org.eclipse.team.ui.PageCompareEditorInput}: a compare editor input whose structure pane is a page (a history
 * page): the page's selected revision is turned into a compare input ({@link #asCompareInput(ISelection)}) and forwarded
 * to the content pane, and a double click opens it.
 */
public abstract class PageCompareEditorInput extends CompareEditorInput {
  private CompareViewerPane pagePane;

  protected PageCompareEditorInput(CompareConfiguration configuration) {
    super(configuration);
  }

  @Override
  protected CompareViewerPane createStructureInputPane(Composite parent) {
    pagePane = new CompareViewerPane(parent, SWT.BORDER | SWT.FLAT) {
      @Override
      public void selectionChanged(SelectionChangedEvent ev) {
        StructuredSelection newSelection = convertSelection(ev.getSelection(), false);
        super.selectionChanged(new SelectionChangedEvent(pagePane, newSelection));
      }

      private StructuredSelection convertSelection(ISelection selection, boolean prepare) {
        ICompareInput ci = asCompareInput(selection);
        if (ci == null)
          return StructuredSelection.EMPTY;
        if (prepare)
          prepareCompareInput(ci);
        return new StructuredSelection(ci);
      }

      @Override
      public ISelection getSelection() {
        ISelectionProvider provider = getSelectionProvider();
        return provider == null ? StructuredSelection.EMPTY : convertSelection(provider.getSelection(), false);
      }

      @Override
      public Object getInput() {
        return getCompareResult();
      }

      @Override
      public void open(OpenEvent event) {
        super.open(new OpenEvent(event.getViewer(), convertSelection(event.getSelection(), true)));
      }

      @Override
      public void doubleClick(DoubleClickEvent event) {
        super.doubleClick(new DoubleClickEvent(event.getViewer(), convertSelection(event.getSelection(), true)));
      }
    };
    ToolBarManager toolBarManager = CompareViewerPane.getToolBarManager(pagePane);
    IPage page = createPage(pagePane, toolBarManager);
    pagePane.setContent(page.getControl());
    hookupListeners();
    return pagePane;
  }

  /** Creates the page and returns its top level control's page. */
  protected abstract IPage createPage(CompareViewerPane parent, ToolBarManager toolBarManager);

  /** The selection provider of the page (called after the page is created). */
  protected abstract ISelectionProvider getSelectionProvider();

  /** Sets the title of the pane that contains the page. */
  protected void setPageDescription(String title) {
    if (pagePane != null)
      pagePane.setText(title);
  }

  @Override
  protected void handleDispose() {
    super.handleDispose();
    cleanupListeners();
  }

  private void hookupListeners() {
    ISelectionProvider selectionProvider = getSelectionProvider();
    if (selectionProvider != null)
      selectionProvider.addSelectionChangedListener(pagePane);
    if (selectionProvider instanceof StructuredViewer sv) {
      sv.addOpenListener(pagePane);
      sv.addDoubleClickListener(pagePane);
    }
  }

  private void cleanupListeners() {
    ISelectionProvider selectionProvider = getSelectionProvider();
    if (selectionProvider != null)
      selectionProvider.removeSelectionChangedListener(pagePane);
    if (selectionProvider instanceof StructuredViewer sv) {
      sv.removeOpenListener(pagePane);
      sv.removeDoubleClickListener(pagePane);
    }
  }

  /** The compare input that represents the selection: the selected element when it is one {@link ICompareInput}. */
  protected ICompareInput asCompareInput(ISelection selection) {
    if (selection instanceof IStructuredSelection ss && ss.size() == 1 && ss.getFirstElement() instanceof ICompareInput ci)
      return ci;
    return null;
  }

  /** Prepares a compare input for the content pane (labels and images); errors are logged. */
  protected final void prepareCompareInput(final ICompareInput input) {
    if (input == null)
      return;
    try {
      prepareInput(input, getCompareConfiguration(), new NullProgressMonitor());
    } catch (InvocationTargetException e) {
      RLogger.logError(e);
    }
  }

  /** Prepares the compare input for display in a content viewer (may be called from a background job). */
  protected abstract void prepareInput(ICompareInput input, CompareConfiguration configuration, IProgressMonitor monitor)
      throws InvocationTargetException;
}
