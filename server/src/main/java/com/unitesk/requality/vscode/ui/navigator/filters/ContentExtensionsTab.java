package com.unitesk.requality.vscode.ui.navigator.filters;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentDescriptor;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentService;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.ContentExtensionsTab}: "Content": the content extensions bound to
 * the viewer, checked when active.
 */
public class ContentExtensionsTab extends CustomizationTab {
  protected ContentExtensionsTab(Composite parent, INavigatorContentService aContentService) {
    super(parent, aContentService);
    createControl();
  }

  private void createControl() {
    createInstructionsLabel("Select the available extensions to show (unchecked extensions will not display content):");
    createTable();
    getTableViewer().setContentProvider((IStructuredContentProvider) input -> ((INavigatorContentService) input).getVisibleExtensions());
    getTableViewer().setLabelProvider(new CommonFilterLabelProvider());
    getTableViewer().setInput(getContentService());
    updateCheckedState();
  }

  private void updateCheckedState() {
    for (INavigatorContentDescriptor visibleExtension : getContentService().getVisibleExtensions())
      if (getContentService().isActive(visibleExtension.getId()))
        getTableViewer().setChecked(visibleExtension, true);
  }
}
