package com.unitesk.requality.core.checker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.script.ScriptException;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.mass.MassCheckersReset;
import com.unitesk.requality.core.search.JSFormula;
import com.unitesk.requality.core.search.JSFormulaHolder;

public class CheckerRulesHolder extends JSFormulaHolder{
  public static String ATTR_RULES = "_checkerRules";
  private TreeNode node;
  public List<JSFormula> rules;
  public CheckerRulesHolder(TreeNode nodeToStore){
    super(nodeToStore);
    java.util.concurrent.locks.Lock l = rwl.writeLock();
    l.lock();
    try{
      node = nodeToStore;
      //node.getTreeDB().addListener(this);//TODO mb option
      node.addChangeListener(new INodeChangeListener() {
        @Override
        public void moved(TreeNode node, TreeNode oldParent) {
        }
        
        @Override
        public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
        }
        
        @Override
        public void deleted(TreeNode source) {
        }
        
        @Override
        public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
          if(what.getAll().contains(ATTR_RULES)){
            changed(node);
          }
        }
        private void changed(TreeNode node){
          l.lock();
          try{
            load(node);
            MassCheckersReset mcr = new MassCheckersReset();
            mcr.execute(node);
            node.getTreeDB().getCheckerContainer().addAllLoadedToQ();//reset checking state and add all nodes to checking list
          }finally{
            l.unlock();
          }
        }
        @Override
        public void attributeChange(TreeNode source, String what, Attribute old) {
          if(what.equals(ATTR_RULES))
            changed(node);
        }
      });
      load(nodeToStore);
      try {
        se.eval(JS_FUNCS);
      } catch (ScriptException e) {
        e.printStackTrace();
      }
    }finally{
      l.unlock();
    }
  }
  private void load(TreeNode nodeToStore) {
    rwl.writeLock().lock();
    try{
      rules = new LinkedList<>();
      Attribute a = nodeToStore.attrs().getAttribute(ATTR_RULES);
      if(a != null && a.getValue() != null){
        ListValue lv = (ListValue) a.getValue();
        if(lv.getType() == AttributeType.LIST){
          List<Object> vals = lv.getValues();
          boolean isnew = true;
          if(vals.size()>0)
          {
            String s = ((ListValue)(vals.get(0))).getValues().get(0).toString();
            if(s.toLowerCase().equals("textnode")||s.toLowerCase().equals("requirement")){
              isnew = false;
            }
          }
          if(isnew){
            for(Object o : vals)
            {
              ListValue lv3 = (ListValue)o;
              Iterator<Object> it = lv3.iterator();
              rules.add(new JSFormula(it.next().toString(),it.next().toString(),this));
          
            }
          }else
            for(int i = 0;i < vals.size()/2; i+=2)
            {
              ListValue lv1 = (ListValue) vals.get(i*2),
                        lv2 = (ListValue) vals.get(i*2+1);
              String tp = (String) lv1.getValues().get(0);
            //  rules.put(tp, new ArrayList<>());
              if(lv2.getType()==AttributeType.LIST){
                for(Object o: lv2.getValues()){
                  ListValue lv3 = (ListValue)o;
                  Iterator<Object> it = lv3.iterator();
                  rules.add(new JSFormula(it.next().toString(), "one.attr(\""+TreeNode.ATTR_TYPE+"\")!= \""+tp+"\"||"+it.next().toString(),this));
                }
              }else{
                Iterator<Object> it = lv2.iterator();
                int k = 0;
                while(it.hasNext())
                  rules.add(new JSFormula("Rule "+(++k), "one.attr(\""+TreeNode.ATTR_TYPE+"\")!= \""+tp+"\"||"+it.next().toString(),this));
              }
            }
          }
        }
      }
      finally
      {
        rwl.writeLock().unlock();
      }
  }
  public void setRules(List<JSFormula> r){
    java.util.concurrent.locks.Lock l = rwl.writeLock();
    l.lock();
    try{
      rules = r;
      save(node);
    }
    finally{
      l.unlock();
    }
  }
  private void save(TreeNode node2) {
    List<Object> r = new ArrayList<>();
    for(JSFormula cr:rules)
    {
      List<Object> val = new ArrayList<>();
      val.add(cr.getName());
      val.add(cr.getFormula());
      r.add(new ListValue(AttributeType.STRING, val));
    }
    node2.attrs().setConstAttribute(ATTR_RULES, new ListValue(AttributeType.LIST, r),
        Flag.HIDDEN);
    node2.commitAttributes();
    
  }
  public List<CheckerProblem> check(TreeNode node, boolean onlyFalse) {
    java.util.concurrent.locks.Lock l = rwl.readLock();
    l.lock();
    try{
      List<JSFormula> cr = rules;
      if(cr == null || cr.size() == 0)
        return null;
      List<CheckerProblem> ret = new LinkedList<>();
      for(JSFormula c:cr)
      {
        Pair<Object, Boolean>fres = null;
        
        fres = c.runOnNode(node);
        if(fres.getSecond() && resultFalse(fres.getFirst()))
        {
          ret.add(new CheckerProblem(
                  node, "problem_rule_violated", "Rule '%s' is violated on '%s'",
        		  new String[] {c.getName(), node.getUserVisibleName()}));
        }else
          if(!fres.getSecond()){
            Object msg = fres.getFirst();
            if(msg == null)
              msg = "returns NULL value";
            ret.add(new CheckerProblem(
                    node, "problem_rule_error", "Rule '%s' throws en error on node '%s'. Error code: %s",
            		new String[] {c.getName(), node.getUserVisibleName(), msg.toString()}));//TODO test
          
          }
      }
      if(ret.size()==0)
        return null;
      return ret;
    }
    finally{
      l.unlock();
    }
  }
  @Override
  public JSFormula addRule(String name, String text, String ... meta) {
    JSFormula f = new JSFormula(name, text, this);
    rules.add(f);
    return f;
  }
  @Override
  public void removeRule(int ind, String ... meta) {
    rules.remove(ind);
  }
  public boolean sameRules(java.util.List<JSFormula> arules) {
    Set<String> st = new HashSet<>();
    if(rules.size()!=arules.size())
      return false;
    
    for(JSFormula j : rules)
      st.add(j.getFormula()+"|"+j.getName());
    for(JSFormula j : arules){
      if(!st.remove(j.getFormula()+"|"+j.getName()))
        return false;
    }
    return st.size()==0;
  }

}
