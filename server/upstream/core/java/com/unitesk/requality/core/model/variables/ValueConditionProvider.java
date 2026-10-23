package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class ValueConditionProvider implements IVariableCondition {
  IValueProvider valueProvider;
  public ValueConditionProvider(IValueProvider oA) {
    valueProvider = oA;
  }

  @Override
  public boolean check(VariablesManager man, TreeNode node, ReportGenerationContext context) {
    if(valueProvider == null)
      return false;
    VariableType vt = valueProvider.getType(man, node, context);
    Object val = valueProvider.getValue(man, node, context);
    if(val == null)
      return false;
    switch(vt) {
      case Boolean: return (Boolean)val;
      case Long: return (int)val>0;
      case Double: return (double)val>0;
      default:
        return !val.toString().equals("0")&&!val.toString().equals("false")&&!val.toString().equals("[]");//TODO remove if list added
    }
  }

}
