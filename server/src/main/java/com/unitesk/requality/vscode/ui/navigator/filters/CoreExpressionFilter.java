package com.unitesk.requality.vscode.ui.navigator.filters;

import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.CoreExpressionFilter}: hides the elements that match the
 * {@code filterExpression} of a common filter.
 */
public class CoreExpressionFilter extends ViewerFilter {
  private final IConfigurationElement filterExpression;

  public CoreExpressionFilter(IConfigurationElement aFilterExpression) {
    filterExpression = aFilterExpression;
  }

  @Override
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    // the children of filterExpression are combined with "and" (CustomAndExpression)
    return !Expressions.evaluateContainer(filterExpression, new EvaluationContext(null, element));
  }
}
