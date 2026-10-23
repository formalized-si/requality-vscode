package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class AndVCondition implements IVariableCondition{
	public IVariableCondition[] conds;

	@Override
	public boolean check(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		for(IVariableCondition c: conds)
			if(!c.check(man, node, context))
				return false;
		return true;
	}
	
}
