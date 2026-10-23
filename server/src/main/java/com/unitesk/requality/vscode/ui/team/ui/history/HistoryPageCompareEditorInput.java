package com.unitesk.requality.vscode.ui.team.ui.history;

import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareViewerPane;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.team.history.LocalHistoryPage;
import com.unitesk.requality.vscode.ui.team.ui.PageCompareEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IPage;
import com.unitesk.requality.vscode.ui.workbench.Page;

/**
 * {@code org.eclipse.team.ui.history.HistoryPageCompareEditorInput}: a history page combined with the compare/merge
 * infrastructure. The page adapts to an {@link IHistoryCompareAdapter} (the local history page does).
 */
public class HistoryPageCompareEditorInput extends PageCompareEditorInput {
  private IHistoryPage historyPage;
  private DialogHistoryPageSite site;
  private final Object object;
  private final IHistoryPageSource pageSource;
  private boolean isReplace;

  /**
   * Creates a history page compare editor input for the page of the page source and the object.
   *
   * @param configuration the compare configuration
   * @param pageSource the page source
   * @param object the object whose history is displayed
   */
  public HistoryPageCompareEditorInput(CompareConfiguration configuration, IHistoryPageSource pageSource, Object object) {
    super(configuration);
    this.pageSource = pageSource;
    this.object = object;
  }

  @Override
  protected Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
    return object;
  }

  @Override
  protected void handleDispose() {
    super.handleDispose();
    if (historyPage != null) {
      historyPage.dispose();
    }
  }

  @Override
  protected IPage createPage(CompareViewerPane parent, ToolBarManager toolBarManager) {
    return createPage(parent, toolBarManager, true);
  }

  /** Creates the history page in the pane (with multi selection when allowed). */
  protected final IPage createPage(CompareViewerPane parent, ToolBarManager toolBarManager, boolean allowMultiSelection) {
    site = new DialogHistoryPageSite(parent.getShell());
    historyPage = (IHistoryPage) pageSource.createPage(object);
    historyPage.setSite(site);
    site.setToolBarManager(toolBarManager);
    if (historyPage instanceof LocalHistoryPage localHistoryPage) {
      localHistoryPage.createControl(parent, allowMultiSelection);
    } else {
      ((Page) historyPage).createControl(parent);
    }
    historyPage.setInput(object);
    String description = historyPage.getDescription();
    setPageDescription(description == null ? "" : description);
    if (getTitle() == null) {
      setTitle(historyPage.getName());
    }
    return (IPage) historyPage;
  }

  @Override
  protected ICompareInput asCompareInput(ISelection selection) {
    ICompareInput compareInput = super.asCompareInput(selection);
    if (compareInput != null) {
      return compareInput;
    }
    if (historyPage instanceof IHistoryCompareAdapter compareAdapter && selection instanceof IStructuredSelection ss
        && ss.size() == 1) {
      return compareAdapter.getCompareInput(ss.getFirstElement());
    }
    return null;
  }

  @Override
  protected ISelectionProvider getSelectionProvider() {
    return site == null ? null : site.getSelectionProvider();
  }

  @Override
  protected void prepareInput(ICompareInput input, CompareConfiguration configuration, IProgressMonitor monitor)
      throws InvocationTargetException {
    if (historyPage instanceof IHistoryCompareAdapter compareAdapter) {
      compareAdapter.prepareInput(input, configuration, monitor);
    }
  }

  /** The history page of this input, or null when it is not created yet. */
  public final IHistoryPage getHistoryPage() {
    return historyPage;
  }

  @Override
  public boolean isEditionSelectionDialog() {
    return isReplaceDialog();
  }

  /** Whether this input is used in a replace dialog. */
  protected boolean isReplaceDialog() {
    return isReplace;
  }

  /** Sets whether this input is used in a replace dialog. */
  public void setReplace(boolean isReplace) {
    this.isReplace = isReplace;
  }

  @Override
  public String getOKButtonLabel() {
    if (isReplaceDialog()) {
      return "&Replace";
    }
    return super.getOKButtonLabel();
  }

  @Override
  public boolean okPressed() {
    if (!isReplaceDialog()) {
      return super.okPressed();
    }
    performReplace(getSelectedEdition() instanceof ICompareInput ci ? ci.getRight() : null);
    return true;
  }

  /** A replace has been requested in the replace dialog; subclasses replace the contents. */
  protected void performReplace(Object selectedObject) {
  }
}
