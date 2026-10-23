package com.unitesk.requality.vscode.ui.navigator.filters;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import com.unitesk.requality.vscode.ui.jface.action.GroupMarker;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.INavigatorFilterService;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchActionConstants;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionGroup;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.FilterActionGroup}: "Filters and Customization..." and "Recent
 * Filters" (the filters changed last, up to five) in the view menu of a common navigator.
 */
public class FilterActionGroup extends ActionGroup {
  private static final String FILTER_ACTION_GROUP = "filterActionGroup";
  private static final String FILTER_ACTION_GROUP_FILTERS_START = FILTER_ACTION_GROUP + "Filters-start";
  private static final String FILTER_ACTION_GROUP_FILTERS_END = FILTER_ACTION_GROUP + "Filters-end";
  private static final int MAX_FILTER_MENU_ENTRIES = 5;

  private SelectFiltersAction selectFiltersAction;
  private IMenuManager filtersMenu;
  private final CommonViewer commonViewer;
  private Deque<ICommonFilterDescriptor> lruFilterDescriptorStack = new ArrayDeque<>();

  public FilterActionGroup(CommonViewer aCommonViewer) {
    commonViewer = aCommonViewer;
    makeActions();
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    IMenuManager menuManager = actionBars.getMenuManager();
    if (menuManager.find(IWorkbenchActionConstants.MB_ADDITIONS) == null)
      menuManager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    menuManager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, new Separator(FILTER_ACTION_GROUP));
    menuManager.appendToGroup(FILTER_ACTION_GROUP, selectFiltersAction);
    menuManager.appendToGroup(FILTER_ACTION_GROUP, new GroupMarker(FILTER_ACTION_GROUP_FILTERS_START));
    menuManager.appendToGroup(FILTER_ACTION_GROUP_FILTERS_START, new Separator(FILTER_ACTION_GROUP_FILTERS_END));
    menuManager.appendToGroup(FILTER_ACTION_GROUP_FILTERS_START, filtersMenu);
  }

  private void makeActions() {
    selectFiltersAction = new SelectFiltersAction(commonViewer, this);
    filtersMenu = new MenuManager("Re&cent Filters");
  }

  /** The menu listener of Eclipse: "Recent Filters" is filled each time the view menu shows. */
  public void updateRecentFilters() {
    filtersMenu.removeAll();
    addLRUFilterActions(filtersMenu);
  }

  private void addLRUFilterActions(IMenuManager manager) {
    if (lruFilterDescriptorStack.isEmpty())
      return;
    INavigatorFilterService filterService = commonViewer.getNavigatorContentService().getFilterService();
    ICommonFilterDescriptor[] filterDescriptors = lruFilterDescriptorStack.toArray(new ICommonFilterDescriptor[0]);
    Arrays.sort(filterDescriptors, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    for (ICommonFilterDescriptor filterDescriptor : filterDescriptors)
      manager.add(new ToggleFilterAction(commonViewer, filterService, filterDescriptor));
  }

  protected void updateFilterShortcuts(ICommonFilterDescriptor[] filterDescriptorChangeHistory) {
    Deque<ICommonFilterDescriptor> oldestFirstStack = new ArrayDeque<>();
    int length = Math.min(filterDescriptorChangeHistory.length, MAX_FILTER_MENU_ENTRIES);
    for (int i = 0; i < length; i++)
      oldestFirstStack.push(filterDescriptorChangeHistory[i]);
    length = Math.min(lruFilterDescriptorStack.size(), MAX_FILTER_MENU_ENTRIES - oldestFirstStack.size());
    for (int i = 0; i < length; i++) {
      ICommonFilterDescriptor filter = lruFilterDescriptorStack.pollFirst();
      if (!oldestFirstStack.contains(filter))
        oldestFirstStack.push(filter);
    }
    lruFilterDescriptorStack = oldestFirstStack;
    updateRecentFilters();
  }
}
