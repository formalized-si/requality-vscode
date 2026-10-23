package com.unitesk.requality.vscode.ui.navigator.filters;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.INavigatorFilterService;

/** {@code org.eclipse.ui.internal.navigator.filters.ToggleFilterAction}: a recently changed filter in "Recent Filters". */
public class ToggleFilterAction extends Action {
  private final ICommonFilterDescriptor descriptor;
  private final INavigatorFilterService filterService;
  private final CommonViewer commonViewer;

  protected ToggleFilterAction(CommonViewer aCommonViewer, INavigatorFilterService aFilterService,
      ICommonFilterDescriptor aFilterDescriptor) {
    super(aFilterDescriptor.getName(), IAction.AS_CHECK_BOX);
    commonViewer = aCommonViewer;
    filterService = aFilterService;
    descriptor = aFilterDescriptor;
    setChecked(filterService.isActive(descriptor.getId()));
  }

  @Override
  public void run() {
    // VSCODE: a menu item does not toggle itself before running (SWT check items do): the new state is the opposite
    boolean toMakeActive = !filterService.isActive(descriptor.getId());
    filterService.setActive(descriptor.getId(), toMakeActive);
    filterService.persistFilterActivationState();
    filterService.updateViewer();
    commonViewer.setSelection(StructuredSelection.EMPTY);
    setChecked(toMakeActive);
  }
}
