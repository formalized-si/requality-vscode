package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class ValueProviderCondition implements IVariableCondition {
  private static final double EPS = 0.0001d;
  public IValueProvider vp1, vp2;
  public CompareType c;
  private enum CheckType{NUMERICAL, STRING}
  public boolean check (VariablesManager man, TreeNode node, ReportGenerationContext context) {
    VariableType vt1 = vp1.getType(man, node, context);
    VariableType vt2 = vp2.getType(man, node, context);
    CheckType ctype = detectCompareType(vt1, vt2);
    Object o1 = vp1.getValue(man, node, context);
    Object o2 = vp2.getValue(man, node, context);
    if(c==CompareType.EQUAL || c==CompareType.NOT_EQUAL) {
      if(ctype == CheckType.STRING) {
        String s1 = o1 == null ? "" : o1.toString();
        String s2 = o2 == null ? "" : o2.toString();
        return c == CompareType.EQUAL?s1.equals(s2):!s1.equals(s2);
      }else {
        Double d1 = VariablesManager.toNumerical(o1, vt1);
        Double d2 = VariablesManager.toNumerical(o2, vt1);
        if(d1 == null) d1 = 0d;
        if(d2 == null) d2 = 0d;
        switch (c) {
          case EQUAL:
            return Math.abs((double)d1-(double)d2)<EPS;
          case LESS:
            return (double)d1 < (double)d2;
          case MORE:
            return (double)d1 > (double)d2;
          case LESS_EQUAL:
            return (double)d1 <= (double)d2;
          case MORE_EQUAL:
            return (double)d1 >= (double)d2;
          case NOT_EQUAL:
            return Math.abs((double)d1-(double)d2)>=EPS;
        }
      }
    }
    return false;
  }
  private CheckType detectCompareType(VariableType vt1, VariableType vt2) {
	return isNumeric(vt1) && isNumeric(vt2) ? CheckType.NUMERICAL: CheckType.STRING;
  }
  private boolean isNumeric(VariableType vt2) {
    return vt2 == VariableType.Long || vt2 == VariableType.Double;
  }
  public void detectCondition(char ch) {
    switch (ch) {
      case '=':
        c = CompareType.EQUAL;
        break;
      case '<':
        c = CompareType.LESS;
        break;
      case '>':
        c = CompareType.MORE;
        break;
    }
  }

}
