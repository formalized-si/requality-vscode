package com.unitesk.requality.vscode.ui.navigator.filters;

import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentDescriptor;

/** {@code org.eclipse.ui.internal.navigator.filters.CommonFilterLabelProvider}: the names of filters and contents. */
public class CommonFilterLabelProvider extends LabelProvider {
  @Override
  public String getText(Object element) {
    if (element instanceof INavigatorContentDescriptor)
      return ((INavigatorContentDescriptor) element).getName();
    if (element instanceof ICommonFilterDescriptor)
      return ((ICommonFilterDescriptor) element).getName();
    return null;
  }
}
