package com.unitesk.requality.tool;

public class BracesHanlder {
  public static String process(String val, IVariableValueProvider prov) {
    int vars = val.indexOf("{");
    int last = 0, pos = 0;
    String ret = "";
    boolean han = false;
    while(vars>-1){
      int varsEnd = val.indexOf("}", vars+1);
      if(varsEnd>-1){
        String newPart = val.substring(last, vars);//before var
        String name = val.substring(vars+1, varsEnd);
        String[] names = name.split("\\|");
        String repl = null;
        for(String vname:names){
          repl = prov.getValue(vname);
          if(repl!=null) {
            han = true;
            last = varsEnd+1;
            newPart += makeFSSafe(repl);
            pos = last;
            break;
          }
        }
        
        if(repl == null){
          newPart += "{"+name+"}";
          last = varsEnd+1;
          pos=last;
        }
        if(newPart.length() == 0) {
          //remove text part
        }else
          ret += newPart;
        vars = val.indexOf("{", pos);
      }
    }
    if(pos<val.length())
      ret+=val.substring(pos);
    return han?ret:val;
  }
  
  private static String makeFSSafe(String repl) {
    return repl.replace(" ", "_").replace(".", "_").replace("__", "_").replaceAll("[^-_A-Za-z0-9а-яА-Я]", ""); 
  }
}
