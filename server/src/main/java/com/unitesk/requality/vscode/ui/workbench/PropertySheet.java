package com.unitesk.requality.vscode.ui.workbench;

/**
 * {@code org.eclipse.ui.views.properties.PropertySheet}: a view that shows one {@link IPage} at a time. The VS Code
 * Properties view is hosted by the server, so this part is only the holder of the current page.
 */
public class PropertySheet extends ViewPart {
  private IPage currentPage;

  /** {@code org.eclipse.ui.views.properties.PropertySheet#getCurrentPage()}. */
  public IPage getCurrentPage() {
    return currentPage;
  }

  /**
   * {@code org.eclipse.ui.views.properties.PropertySheet#isPinned()}: the pin state of the sheet. The VS Code Properties
   * view has no pin, so the sheet stays unpinned.
   */
  // VSCODE: the VS Code Properties view has no pin action; the sheet is never pinned.
  public boolean isPinned() {
    return false;
  }

  /** The page shown by this sheet (set by its owner). */
  public void setCurrentPage(IPage page) {
    currentPage = page;
  }
}
