package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class VariableOpVariableProvider implements IValueProvider {
  private enum ChangeOperator{ADD,REMOVE,DIVIDE, MULTIPLY}
  IValueProvider oA;
  IValueProvider oB;
  ChangeOperator op;
  
  public VariableOpVariableProvider(IValueProvider oA, IValueProvider oB, char ch) {
    this.oA = oA;
    this.oB = oB;
    op = detectOperation(ch);
  }
  
   ChangeOperator detectOperation(char ch) {
    switch (ch) {
      case '-':
        return ChangeOperator.REMOVE;
      case '*':
        return ChangeOperator.MULTIPLY;
      case '+':
        return ChangeOperator.ADD;
      case '/':
        return ChangeOperator.DIVIDE;
    }
    return null;
  }


  @Override
  public Object getValue(VariablesManager man, TreeNode node, ReportGenerationContext context) {
    VariableType type = getType(man, node, context);
    Object val1 = oA.getValue(man, node, context);
    Object val2 = oB.getValue(man, node, context);
    if(type == null)
      type = detectType(val1, val2);
    if(type == VariableType.Double || type == VariableType.Long) {
      switch(op) {
        case ADD:
          if((val1 == null || (val1 instanceof Long)) && (val2 == null || (val2 instanceof Long)))
            return (val1==null?0:(Long)val1)+(val2==null?0:(Long)val2);
          return (val1==null?0:toDouble(val1))+
              (val2==null?0:toDouble(val2));
        case REMOVE:
          if((val1 == null || (val1 instanceof Long)) && (val2 == null || (val2 instanceof Long)))
            return (val1==null?0:(Long)val1)-(val2==null?0:(Long)val2);
          return (val1==null?0:toDouble(val1))-
              (val2==null?0:toDouble(val2));
        case MULTIPLY:
          if((val1 == null || (val1 instanceof Long)) && (val2 == null || (val2 instanceof Long)))
            return (val1==null?0:(Long)val1)*(val2==null?0:(Long)val2);
          return (val1==null?0:toDouble(val1))*
              (val2==null?0:toDouble(val2));
        case DIVIDE:
          if(val2 == null || (val2 instanceof Long))
          return Double.POSITIVE_INFINITY;
          if((val1 == null || (val1 instanceof Long)) && (val2 instanceof Long))
                     return val1==null?0:(Long)val1 / (Long)val2;
          return (val2==null || toDouble(val2)==0d)?Double.POSITIVE_INFINITY:
             (val1==null?0:toDouble(val1))/
              toDouble(val2);
        }
    }else if (type == VariableType.String) {
      switch(op) {
        case ADD:
          return toString(val1)+toString(val2);
      }
    }
    return null;
  }

  private VariableType detectType(Object oa, Object ob) {
    if(oa!=null) {
      if(oa instanceof String)
        return VariableType.String;
      if(oa instanceof Double)
        return VariableType.Double;
      if(oa instanceof Boolean)
        return VariableType.Boolean;
      if(oa instanceof Integer)
        return VariableType.Long;
      if(oa instanceof Long)
        return VariableType.Long;
    }
    if(ob!=null)
      return detectType(ob, null);
    return null;
  }

  private String toString(Object val) {
    if(val == null)
      return "";
    if(val instanceof Double )
      return val.toString();
    if(val instanceof Long)
      return val.toString();
    return val.toString();//TODO?)
  }

  private Double toDouble(Object val2) {
    if(val2==null)
      return null;
    if(val2 instanceof Long)
      return (Long)val2+0d;
    return (double)val2;
  }

  @Override
  public VariableType getType(VariablesManager man, TreeNode node, ReportGenerationContext context) {
    VariableType vt = oA.getType(man, node, context);
    return vt!=null ? vt: oB.getType(man, node, context);
  }

}
