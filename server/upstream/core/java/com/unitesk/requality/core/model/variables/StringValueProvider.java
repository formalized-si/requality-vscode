package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class StringValueProvider implements IValueProvider {
	public String value;
	@Override
	public Object getValue(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		return value;
	}

	@Override
	public VariableType getType(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		return VariableType.String;
	}

}
