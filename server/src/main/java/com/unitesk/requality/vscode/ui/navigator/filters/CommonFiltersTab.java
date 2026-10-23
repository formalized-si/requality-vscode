package com.unitesk.requality.vscode.ui.navigator.filters;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.regex.Pattern;

import com.unitesk.requality.vscode.ui.jface.viewers.CheckStateChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentService;
import com.unitesk.requality.vscode.ui.navigator.INavigatorFilterService;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.CommonFiltersTab}: "Pre-set filters": the filters shown in the UI,
 * sorted by name, filtered by the text typed above them, checked when active.
 */
public class CommonFiltersTab extends CustomizationTab {
  private Text filterText;
  private final CommonFilterLabelProvider filterLabelProvider = new CommonFilterLabelProvider();
  private final TablePatternFilter patternFilter = new TablePatternFilter();
  private final Deque<ICommonFilterDescriptor> filterDescriptorChangeHistory = new ArrayDeque<>();

  protected CommonFiltersTab(Composite parent, INavigatorContentService aContentService) {
    super(parent, aContentService);
    createControl();
  }

  @Override
  protected void checkStateChanged(CheckStateChangedEvent event) {
    super.checkStateChanged(event);
    ICommonFilterDescriptor filterDescriptor = (ICommonFilterDescriptor) event.getElement();
    filterDescriptorChangeHistory.remove(filterDescriptor);
    filterDescriptorChangeHistory.push(filterDescriptor);
  }

  protected ICommonFilterDescriptor[] getFilterDescriptorChangeHistory() {
    return filterDescriptorChangeHistory.toArray(new ICommonFilterDescriptor[0]);
  }

  private void createControl() {
    createInstructionsLabel("Select the filters to apply (matching items will be hidden):");
    createPatternFilterText(this);
    createTable();
    getTableViewer().setContentProvider((IStructuredContentProvider) input -> {
      ICommonFilterDescriptor[] d = ((INavigatorContentService) input).getFilterService().getVisibleFilterDescriptorsForUI();
      // CommonFilterComparator: by name
      ICommonFilterDescriptor[] sorted = d.clone();
      Arrays.sort(sorted, (a, b) -> String.valueOf(a.getName()).compareTo(String.valueOf(b.getName())));
      return sorted;
    });
    getTableViewer().setLabelProvider(filterLabelProvider);
    getTableViewer().setInput(getContentService());
    getTableViewer().addFilter(patternFilter);
    updateFiltersCheckState();
  }

  private void createPatternFilterText(Composite composite) {
    filterText = new Text(composite, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
    filterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    filterText.setMessage("enter name of filter");
    filterText.addModifyListener(e -> textChanged());
  }

  void setInitialFocus() {
    filterText.forceFocus();
  }

  private void textChanged() {
    patternFilter.setPattern(filterText.getText());
    getTableViewer().refresh();
    for (Object checkedItem : getCheckedItems())
      getTableViewer().setChecked(checkedItem, true);
  }

  private void updateFiltersCheckState() {
    INavigatorFilterService filterService = getContentService().getFilterService();
    for (ICommonFilterDescriptor filterDescriptor : filterService.getVisibleFilterDescriptorsForUI()) {
      if (filterService.isActive(filterDescriptor.getId())) {
        getTableViewer().setChecked(filterDescriptor, true);
        getCheckedItems().add(filterDescriptor);
      } else {
        getTableViewer().setChecked(filterDescriptor, false);
      }
    }
  }

  /** The filters whose names contain the text typed (StringMatcher "*text*", ignoring case). */
  private class TablePatternFilter extends ViewerFilter {
    private Pattern matcher;

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
      return match(filterLabelProvider.getText(element));
    }

    protected void setPattern(String newPattern) {
      if (newPattern == null || newPattern.trim().isEmpty()) {
        matcher = null;
        return;
      }
      StringBuilder re = new StringBuilder(".*");
      for (char c : newPattern.toCharArray())
        re.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
      matcher = Pattern.compile(re.append(".*").toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    protected boolean match(String input) {
      return input != null && (matcher == null || matcher.matcher(input).matches());
    }
  }
}
