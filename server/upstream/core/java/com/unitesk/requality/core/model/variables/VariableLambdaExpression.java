package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class VariableLambdaExpression implements IValueProvider{
  public IVariableCondition condition;
  public IValueProvider valueIfCond;
  public IValueProvider valueIFNotCond;
  @Override
  public Object getValue(VariablesManager man, TreeNode node, ReportGenerationContext context) {
    if(condition == null)
      return null;
    if(condition.check(man, node, context))
      return valueIfCond!=null?valueIfCond.getValue(man, node, context):null;
    return valueIFNotCond!=null?valueIFNotCond.getValue(man, node, context):null;
  }

  @Override
  public VariableType getType(VariablesManager man, TreeNode node, ReportGenerationContext context) {
    return valueIfCond!=null?valueIfCond.getType(man, node, context):VariableType.String;
  }
}
