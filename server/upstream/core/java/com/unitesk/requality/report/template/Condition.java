package com.unitesk.requality.report.template;

import com.unitesk.requality.core.model.TreeNode;

public class Condition implements ConditionContainer{
  private String op1;
  private Condition opc1;
  private String op2;
  private Condition opc2;
  private ConditionOp opd = ConditionOp.EQ;
  @Override
  public void setContainer(Condition c, int pos) {
    if(pos==0) {
      opc1 = c;
      op1 = null;
    }else {
      opc2 = c;
      op2 = null;
    }
  }
  public void setCondition1(Condition c1) {
	  opc1 = c1;
	  op1 = null;
  }
  public void setCondition2(Condition c2) {
	  opc2 = c2;
	  op2 = null;
  }
  public void setOp1(String c2) {
	  opc1 = null;
	  op1 = c2;
  }
  public void setOp2(String c2) {
	  opc2 = null;
	  op2 = c2;
  }
  public void setOperation(String cond){
  	opd = ConditionOp.read(cond);
  }
  public boolean check(TreeNode node) {
    //TODO use it and check
	return false;
  }
  public String getOperationString() {
    if(opd!=null)
      return opd.toString();
    return null;
  }
  public Object getFirstOp() {
	if(op1!=null)
	  return op1;
	return opc1;
  }
  public Object getSecondOp() {
	if(op2!=null)
	  return op2;
	return opc2;
  }
}
