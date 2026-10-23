package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class EqVCondition implements IVariableCondition{
	public IVariableCondition[] conds;

	@Override
	public boolean check(VariablesManager man, TreeNode node, ReportGenerationContext context) {
      if(conds.length == 0)
        return true;
      Boolean first = conds[0].check(man, node, context);
      for(int i = 1; i< conds.length; i++)
        if(conds[i].check(man, node, context)!=first)
          return false;
      return true;
	}
	
}
