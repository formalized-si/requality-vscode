package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class VariableValueProvider implements IValueProvider {
	private VariableDefinition vd;
	public VariableValueProvider (VariableDefinition vd)
	{
		this.vd = vd;
	}
	@Override
	public Object getValue(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		return man.getValue(vd, node, context);
	}
	@Override
	public VariableType getType(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		return man.getType(vd, node, context);
	}

}
