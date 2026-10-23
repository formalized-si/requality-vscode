package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public interface IValueProvider {
  public Object getValue(VariablesManager man, TreeNode node, ReportGenerationContext context);
  public VariableType getType(VariablesManager man, TreeNode node, ReportGenerationContext context);
}
