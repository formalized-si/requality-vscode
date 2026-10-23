package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public interface IVariableChange {
	IVariableChange NO_CHANGE = new IVariableChange() {
		@Override
		public boolean apply(VariablesManager man, TreeNode node, ReportGenerationContext context) {
			return true;
		}
	};
	IVariableChange[] NO_CHANGES = new IVariableChange[] {NO_CHANGE};
	public boolean apply (VariablesManager man, TreeNode node, ReportGenerationContext context);
}
