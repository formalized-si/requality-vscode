package com.unitesk.requality.vscode.ui.navigator.filters;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;

/** {@code org.eclipse.ui.internal.navigator.filters.SelectFiltersAction}: "Filters and Customization..." of the view menu. */
public class SelectFiltersAction extends Action {
  private final CommonViewer commonViewer;
  private final FilterActionGroup filterGroup;

  public SelectFiltersAction(CommonViewer aCommonViewer, FilterActionGroup aFilterGroup) {
    super("&Filters and Customization...");
    setToolTipText("Select and deselect filters to apply to the content in the tree");
    commonViewer = aCommonViewer;
    filterGroup = aFilterGroup;
  }

  @Override
  public void run() {
    CommonFilterSelectionDialog filterSelectionDialog = new CommonFilterSelectionDialog(commonViewer);
    if (filterSelectionDialog.open() == Window.OK)
      filterGroup.updateFilterShortcuts(filterSelectionDialog.getFilterDescriptorChangeHistory());
  }
}
