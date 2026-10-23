package com.unitesk.requality.core.search;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;

public class JSFormula {
  private String rule;
  private String name;
  private JSFormulaHolder crh;
  private Bindings b = null;
  public synchronized Bindings getBinding() {
    if(b == null)
      b = crh.sse.createBindings();
    return b;
  }
  public JSFormula(String name, String rule, JSFormulaHolder crh) {
    this.rule = rule;
    this.name = name;
    this.crh = crh;
  }

  final ScriptContext ctxt = new SimpleScriptContext();
  public Pair<Object, Boolean> runOnNode(TreeNode node) {
    synchronized (this) {//sync for formula reuse
      synchronized (crh.em) {//sync by element initiation
        getBinding().put("one", crh.em.getElement(node));
        ctxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
      }
      try{
        return new Pair<>(crh.se.eval(rule,ctxt), true);
      }catch(ScriptException e){
        return new Pair<>(e.getMessage(), false);
      } finally {
        b.clear();
      }
  	}
  }

  public String getFormula() {
    return rule;
  }

  public void setFormula(String text) {
    rule = text;
  }

  public String getName() {
    return name;
  }
  public void setName(String name){
    this.name = name;
  }
  
}
