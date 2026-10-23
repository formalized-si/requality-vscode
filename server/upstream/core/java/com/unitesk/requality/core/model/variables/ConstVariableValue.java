package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class ConstVariableValue implements IValueProvider{
	public boolean isDouble = true;
	public long consL = 0;
	public double consD = 0;
	public ConstVariableValue(Object val, boolean isDouble) {
		this.isDouble = isDouble;
		if(isDouble)
			this.consD = (double) val;
		else
			this.consL = (long) val;
	}
	@Override
	public Object getValue(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		if(isDouble)
			return consD;
		return consL;
	}
	@Override
	public VariableType getType(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		return isDouble?VariableType.Double:VariableType.Long;
	}
	
}
