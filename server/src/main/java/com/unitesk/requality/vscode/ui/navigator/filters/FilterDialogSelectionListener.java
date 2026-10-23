package com.unitesk.requality.vscode.ui.navigator.filters;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentDescriptor;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/** {@code org.eclipse.ui.internal.navigator.filters.FilterDialogSelectionListener}: the description of the selected item. */
public class FilterDialogSelectionListener implements ISelectionChangedListener {
  private final Label descriptionText;

  protected FilterDialogSelectionListener(Label aDescriptionText) {
    descriptionText = aDescriptionText;
  }

  @Override
  public void selectionChanged(SelectionChangedEvent event) {
    Object element = event.getSelection() instanceof IStructuredSelection
        ? ((IStructuredSelection) event.getSelection()).getFirstElement() : null;
    if (element instanceof INavigatorContentDescriptor) {
      descriptionText.setText("Provides content from the \"" + ((INavigatorContentDescriptor) element).getName() + "\" extension.");
    } else if (element instanceof ICommonFilterDescriptor) {
      ICommonFilterDescriptor cfd = (ICommonFilterDescriptor) element;
      String description = cfd.getDescription();
      descriptionText.setText(description != null ? description : "Enable the " + cfd.getName() + " filter.");
    }
  }
}
