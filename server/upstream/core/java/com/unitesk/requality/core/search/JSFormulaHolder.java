package com.unitesk.requality.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.script.ElementMapper;
import com.unitesk.requality.tool.CoreUtils;

public class JSFormulaHolder {
  public static String JS_FUNCS = "function varEquals(vara, val){\n" +
      "    return typeof (vara) !== 'undefined' && vara.getValue() == val;\n" +
     "}\n" +
     "function isEnum(vara){\n"+
       "return typeof(vara)!== 'undefined' && vara.getType() == \"ENUM\";\n"+
     "}\n"+
     "function isString(vara){\n"+
       "return typeof(vara)!== 'undefined' && vara.getType() == \"STRING\";\n"+
     "}\n"+
     "function isBoolean(vara){\n"+
       "return typeof(vara)!== 'undefined' && vara.getType() == \"BOOL\";\n"+
     "}\n"+
     "function isInt(vara){\n"+
       "return typeof(vara)!== 'undefined' && vara.getType() == \"INT\";\n"+
     "}\n"+
     "function isList(vara){\n"+
       "return typeof(vara)!== 'undefined' && vara.getType() == \"LIST\";\n"+
     "}\n"+
     "function contains(vara, val){\n"+
       "if(!isList(vara))\n"+
         "return false;\n"+
       "return false;\n"+ 
     "}\n"+
     "function isNodeRequirement(){\n"+
       "return getNodeType()==\"Requirement\";\n"+ 
     "}\n"+
     "function isNodeTextNode(){\n"+
       "return getNodeType()==\"TextNode\";\n"+ 
     "}\n"+
     "function getVarValue(vara,defa){\n"+
        "return typeof(vara)!=='undefined'?vara.getValue():defa;\n"+ 
     "}\n"+
     "function getNodeType(){\n"+
       "return getVarValue(Type, \"\");\n"+ 
     "}\n"+
     "function getNodeName(){\n"+
       "return getVarValue(Name, \"\");\n"+ 
     "}\n"+
     "function getNodeId(){\n"+
     "return getVarValue(Id, \"\");\n"+ 
   "}\n"+
     "function getNodeDescription(){\n"+
       "return getVarValue(Description, \"\");\n"+ 
     "}\n"+
     "function getNodeVerson(){\n"+
       "return getVarValue(Version, \"\");\n"+ 
     "}\n"+
     "function tos(){\n"+
       "return this.getValue();\n"+
     "}";
  public static String ATTR_RULES = "_checkerRules";
  static ScriptEngineManager smanager;
  static ScriptEngine sse = CoreUtils.getJSEngine();
  static ElementMapper sem;
  ScriptEngineManager manager;
  protected ScriptEngine se;
  ElementMapper em;
  
  protected ReadWriteLock rwl = new ReentrantReadWriteLock();
  public JSFormulaHolder(TreeNode nodeToStore){
    java.util.concurrent.locks.Lock l = rwl.writeLock();
    try{
      l.lock();
      manager = getManager();
      se = getEngine();
      //se = manager.getEngineByName("javascript");//
      em = getMapper();//new ElementMapper();
      try {
        se.eval(JS_FUNCS);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }finally{
      l.unlock();
    }
  }
  private static ElementMapper getMapper() {
    if(sem == null)
      sem = new ElementMapper();
    return sem;
  }
  private static ScriptEngineManager getManager() {
    if(smanager == null)
      smanager = new ScriptEngineManager();
    return smanager;
  }
  private static ScriptEngine getEngine() {
    return CoreUtils.getJSEngine();
  }
  List<JSFormula> fholder = new ArrayList<>();
  public JSFormula addRule(String name, String text, String ... meta) {
    JSFormula f = new JSFormula(name, text, this);
    fholder.add(f);
    return f;
  } 
  public void removeRule(int ind, String ... meta) {
    fholder.remove(ind);
  }
  public void removeRule(JSFormula f, String ... meta) {
    fholder.remove(f);
  }
  public static boolean resultNotTrue(Object msg) {
    return msg == null || !Boolean.TRUE.equals(msg);
  }
  public static boolean resultFalse(Object msg) {
    return msg != null && Boolean.FALSE.equals(msg);
  }
  
  public JSFormula getRule(int i, String ... meta) {
    if(i<fholder.size()&&i>-1)
      return fholder.get(i);
    return null;
  }

}
