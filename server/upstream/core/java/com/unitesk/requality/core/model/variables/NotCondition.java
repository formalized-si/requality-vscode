package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class NotCondition implements IVariableCondition{
	public IVariableCondition cond;

	@Override
	public boolean check(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		return !cond.check(man, node, context);
	}
}
