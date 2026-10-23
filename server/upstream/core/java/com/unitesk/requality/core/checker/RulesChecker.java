package com.unitesk.requality.core.checker;

import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.model.variables.ConditionBuilder;
import com.unitesk.requality.core.model.variables.IVariableCondition;
import com.unitesk.requality.core.model.variables.VariablesManager;
import com.unitesk.requality.nodetype.Requirement;

public class RulesChecker implements INodeChecker {

  public RulesChecker() {}
  public static long time1 = 0, time2 = 0;
  private static List<IVariableCondition> conditions = new LinkedList<>();
  static {
	for(String s:new String[] {"!(Vnode._shownChildren)|!(Vnode._parent._id='')|!(Vnode._type='Requirement')|(Vnode.Вид='Заголовок')|!(Vnode.Вид)"}) 
      conditions.add(ConditionBuilder.parseCondition(s));
  }
  private static VariablesManager man = new VariablesManager(null,null);
  
  @Override
  public boolean check(TreeNode node, List<IMark> result) {
    // Set<String> attrs = node.getAttributes().getAttributeKeys();
    if (node.isRoot()||!node.getQualifiedId().startsWith(Requirement.getTypeRootQId())) // ignore attrs stored in root
      return true;
    List<CheckerProblem> msg = node.getTreeDB().getAttributeRules().getCheckerRules().check(node, true);
    if(msg!=null){
      if(msg.size()>0) {
    	int errc = result.size();
	    long t1 = System.currentTimeMillis();
        for(CheckerProblem s:msg)
          result.add(s);
        time1+=System.currentTimeMillis()-t1;
        long t2 = System.currentTimeMillis();
        int cnt = 0;
        for(IVariableCondition cond:conditions){
          if(!cond.check(man, node, null))
            System.out.println("rule "+cond.toString()+" is perh on "+node.getSUUId());
          else
            if(errc!=msg.size()&&cond.check(man, node, null))
              cnt++;
              //System.out.println("rule rigth "+node.getSUUId());
        }
        time2+=System.currentTimeMillis()-t2;
        return false;
      }
    }
    
    return true;
  }
}
