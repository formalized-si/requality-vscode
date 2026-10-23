package com.unitesk.requality.core.model.variables;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import com.unitesk.requality.core.log.RLogger;

public class StringParser {
  /**
   * Преобразовать строку в обратную польскую нотацию
   * 
   * @param sIn Входная строка
   * @return Выходная строка в обратной польской нотации
   */
  public static String opn(String sIn) {
    StringBuilder sbStack = new StringBuilder(), sbOut = new StringBuilder();
    char cIn, cTmp;

    for (int i = 0; i < sIn.length(); i++) {
      cIn = sIn.charAt(i);
      if (isOp(cIn)) {
        while (sbStack.length() > 0) {
          cTmp = sbStack.substring(sbStack.length() - 1).charAt(0);
          if (isOp(cTmp) && (opPrior(cIn) <= opPrior(cTmp))) {
            sbOut.append(" ").append(cTmp).append(" ");
            sbStack.setLength(sbStack.length() - 1);
          } else {
            sbOut.append(" ");
            break;
          }
        }
        sbOut.append(" ");
        sbStack.append(cIn);
      } else if ('(' == cIn) {
        sbStack.append(cIn);
      } else if (')' == cIn) {
        cTmp = sbStack.substring(sbStack.length() - 1).charAt(0);
        while ('(' != cTmp) {
          if (sbStack.length() < 1) {
            return null;
          }
          sbOut.append(" ").append(cTmp);
          sbStack.setLength(sbStack.length() - 1);
          cTmp = sbStack.substring(sbStack.length() - 1).charAt(0);
        }
        sbStack.setLength(sbStack.length() - 1);
      } else {
        // Если символ не оператор - добавляем в выходную последовательность
        sbOut.append(cIn);
      }
    }

    // Если в стеке остались операторы, добавляем их в входную строку
    while (sbStack.length() > 0) {
      sbOut.append(" ").append(sbStack.substring(sbStack.length() - 1));
      sbStack.setLength(sbStack.length() - 1);
    }

    return sbOut.toString();
  }

  public static Object parseCond(String sIn) {
    double dA, dB;
    long lB;
    String sTmp;
    VariableDefinition vd;
    Deque<Object> stack = new ArrayDeque<>();
    StringTokenizer st = new StringTokenizer(sIn);
    boolean isVariable = false;
    while (st.hasMoreTokens()) {
      try {
        sTmp = st.nextToken().trim();
        vd = null;
        isVariable = false;
        if (sTmp.startsWith("V")) {
          sTmp = sTmp.substring(1);
          vd = VariableDefinition.fromString(sTmp);
          isVariable = true;
        }
        if (1 == sTmp.length() && isOp(sTmp.charAt(0))) {
          if (stack.size() < 2 && sTmp.charAt(0)!='!' || stack.size() < 1 && sTmp.charAt(0)=='!') {
            throw new RuntimeException("Неверное количество данных в стеке для операции " + sTmp);
          }
          boolean singleArg = sTmp.charAt(0)=='!';
          Object oB = stack.pop();
          Object oA = singleArg?null:stack.pop();
          Object oR = null;
          char ch = sTmp.charAt(0);
          // Считываем первый символ
          if (oB instanceof Double && oA instanceof Double)
            oR = calcDoubleResult((Double) oA, (Double) oB, ch);
          else {// TODO rewrite by valueproviders+
            if (isLogical(ch)) {
              if (oA instanceof VariableDefinition) {
                VariableCondition vc = new VariableCondition();
                if(vd == null)
                  vd = (VariableDefinition)oA;
                vc.vd = vd;
                vc.detectCondition(ch);
                if (oB instanceof Double) {
                  vc.targetConst = (Double) oB;
                  oR = vc;
                } else if (oB instanceof String){
                  String s = (String) oB;
                  if(s.startsWith("'") && s.endsWith("'") || s.startsWith("\"") && s.endsWith("\"")) {
                    vc.targetString = s;
                  	oR = vc;
                  }else
                    throw new RuntimeException("Incorrect sequence");  
                } else
                  throw new RuntimeException("Incorrect sequence");
                
              } else {
                if (ch == '!') {
                  NotCondition nc = new NotCondition();
                  IVariableCondition ivp = null;
                  if(oB instanceof VariableDefinition) {
                    VariableDefinition vda = (VariableDefinition) oB;
                    VariableValueProvider vvp = new VariableValueProvider(vda);
                    ValueConditionProvider vcfv = new ValueConditionProvider(vvp);
                    ivp = vcfv;
                  }else if(oB instanceof IValueProvider){
                    //special case for value-related condition
                    IValueProvider vp = (IValueProvider) oB;
                    ValueConditionProvider vcfv = new ValueConditionProvider(vp);
                    ivp = vcfv;
                  }else {
                    ivp = (IVariableCondition) oB;
                  }
                  nc.cond = ivp;
                  oR = nc;
                }else if (ch == '&') {
                  AndVCondition cCond = new AndVCondition();
                  oR = cCond;
                  cCond.conds = new IVariableCondition[] { (IVariableCondition) oA,
                      (IVariableCondition) oB };
                } else if (ch == '|') {
                  OrVCondition cCond = new OrVCondition();
                  oR = cCond;
                  cCond.conds = new IVariableCondition[] { (IVariableCondition) oA,
                      (IVariableCondition) oB };
                } else if (ch == '=') {
                  EqVCondition cCond = new EqVCondition();
                  oR = cCond;
                  cCond.conds = new IVariableCondition[] { (IVariableCondition) oA,
                      (IVariableCondition) oB };
                }
              }
            }
          }
          if (oR != null)
            stack.push(oR);
          else
            RLogger.logDebug("Operation result was null", 2);
        } else {
          if (isVariable) {
            vd = VariableDefinition.fromString(sTmp);
            stack.push(vd);
          } else {
            if(sTmp.startsWith("'") && sTmp.endsWith("'") || (sTmp.startsWith("\"") && sTmp.endsWith("\\\""))){
              stack.push(sTmp);//const
            }else {
              dA = Double.parseDouble(sTmp);
              stack.push(dA);
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("Недопустимый символ в выражении");
      }
    }

    if (stack.size() > 1) {
      throw new RuntimeException("Количество операторов не соответствует количеству операндов");
    }

    return stack.pop();
  }

  private static boolean isLogical(char ch) {
    switch (ch) {
    case '=':
    case '<':
    case '>':
    case '&':
    case '|':
    case '!':
      return true;
    }
    return false;
  }

  private static boolean isCalculation(char ch) {
    switch (ch) {
    case '+':
    case '-':
    case '/':
    case '*':
    case '?':
    case ':':
      return true;
    }
    return false;
  }

  private static Object calcDoubleResult(double dA, double dB, char ch) {
    switch (ch) {
    case '+':
      return dA += dB;
    case '-':
      return dA -= dB;
    case '/':
      return dA /= dB;
    case '*':
      return dA *= dB;
    case '%':
      return dA %= dB;
    case '^':
      return dA = Math.pow(dA, dB);
    case '=':
      return dA == dB;
    case '<':
      return dA < dB;
    case '>':
      return dA < dB;
    }
    throw new RuntimeException("Unknown operation");
  }

  /**
   * Функция проверяет, является ли текущий символ оператором
   */
  private static boolean isOp(char c) {
    switch (c) {
    case ':':
    case '?':
    case '-':
    case '+':
    case '*':
    case '/':
    case '>':
    case '<':
    case '=':
    case '&':
    case '|':
    case ',':
    case '!':
      return true;
    }
    return false;
  }

  /**
   * Возвращает приоритет операции
   * 
   * @param op char
   * @return byte
   */
  private static byte opPrior(char op) {
    switch (op) {
    case '!':
      return 5;
    case ':':
    case '?':
      return 4;
    case '^':
      return 3;
    case '*':
    case '/':
    case '%':
    case '&':
    case '|':
      return 2;
    case '=':
      return 1;
    default:
      return 0;
    }
    // return 1; // Тут остается + и -
  }

  public static IValueProvider parseValueProvider(String sIn) {
    double dA, dB;
    long lA, lB;
    String sTmp;
    Deque<Object> stack = new ArrayDeque<>();
    StringTokenizer st = new StringTokenizer(sIn);
    ValueType pnt = ValueType.CONST;
    boolean first = true;
    VariableDefinition vd;
    while (st.hasMoreTokens()) {
      pnt = ValueType.CONST;
      vd = null;
      String nodeId = null;
      try {
        sTmp = st.nextToken().trim();
        vd = VariableDefinition.fromString(sTmp);
        if (1 == sTmp.length() && isOp(sTmp.charAt(0))) {
          if (stack.size() < 2) {
            throw new RuntimeException("Неверное количество данных в стеке для операции " + sTmp);
          }
          Object oB = stack.pop();
          Object oA = stack.pop();
          Object oR = null;
          char ch = sTmp.charAt(0);
          if(ch == '?') {
            if(oA instanceof VariableValueProvider)
             oA = new ValueConditionProvider((VariableValueProvider)oA);
            if(oA instanceof IVariableCondition) {
              if(oB instanceof IValueProvider)
              {
                VariableLambdaExpression vle = new VariableLambdaExpression();
                vle.condition = (IVariableCondition)oA;
                vle.valueIfCond = (IValueProvider)oB;
                oR = vle;
              }else
                throw new RuntimeException("Incorrect sequence");
            }else
              throw new RuntimeException("Incorrect sequence");
          }else if (ch == ':') { 
            if(oA instanceof VariableLambdaExpression) {
              if(oB instanceof IValueProvider)
              {
                VariableLambdaExpression vle = (VariableLambdaExpression) oA;
                vle.valueIFNotCond = (IValueProvider)oB;
                oR = vle;
              }else
                throw new RuntimeException("Incorrect sequence");
            }else
              throw new RuntimeException("Incorrect sequence");
          }else
            if (oB instanceof Double && oA instanceof Double)
              oR = calcDoubleResult((Double) oA, (Double) oB, ch);
            else {
              if (isCalculation(ch)) {
                if (!(oA instanceof IValueProvider))
                  oA = toValueProvider(oA);
                if (!(oB instanceof IValueProvider))
                  oB = toValueProvider(oB);
                if (oA instanceof IValueProvider && oB instanceof IValueProvider)
                  oR = new VariableOpVariableProvider((IValueProvider) oA, (IValueProvider) oB, ch);
              }else
                if(isLogical(ch)) {
                  if (oA instanceof IValueProvider) {
                    ValueProviderCondition vc = new ValueProviderCondition();
                    vc.detectCondition(ch);
                    vc.vp1 = (IValueProvider) oA;
                    if (oB instanceof IValueProvider) {
                      vc.vp2 = (IValueProvider) oB;
                      oR = vc;
                    } else
                      throw new RuntimeException("Incorrect sequence");
                  } else {
                    if (ch == '&') {
                      AndVCondition cCond = new AndVCondition();
                      oR = cCond;
                      cCond.conds = new IVariableCondition[] { (IVariableCondition) oA,
                          (IVariableCondition) oB };
                    } else if (ch == '|') {
                      OrVCondition cCond = new OrVCondition();
                      oR = cCond;
                      cCond.conds = new IVariableCondition[] { (IVariableCondition) oA,
                          (IVariableCondition) oB };
                    } else if (ch == '=') {
                      EqVCondition cCond = new EqVCondition();
                      oR = cCond;
                      cCond.conds = new IVariableCondition[] { (IVariableCondition) oA,
                          (IVariableCondition) oB };
                    }
                  }
                }
            }
          stack.push(oR);
        } else {
          ValueType vt = ValueType.CONST;
          if(vd!=null)
            vt = vd.variableType;
          switch(vt) {
            default:{
              VariableValueProvider vvp = new VariableValueProvider(vd);
              stack.push(vvp);
            }break;
            case STRING_CONST: {
              if(vd!=null) {
                StringValueProvider svp = new StringValueProvider();
                svp.value = replaceQuoteConstsBack(vd.variableName);
                stack.push(svp);
              }
            }
            break;
            case CONST: {
              boolean isLong = false;
              Object cval = null;
              try {
                cval = Long.parseLong(sTmp);
                isLong = true;
              } catch (NumberFormatException nfe) {
                cval = Double.parseDouble(sTmp);
              }
              ConstVariableValue r = new ConstVariableValue(cval, !isLong);
              stack.push(r);
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("Недопустимый символ в выражении");
      }
    }

    if (stack.size() > 1) {
      throw new RuntimeException("Количество операторов не соответствует количеству операндов");
    }
    Object ret = stack.pop();
    return (IValueProvider) ret;
  }

  private static IValueProvider toValueProvider(Object oA) {
    if (oA instanceof Long)
      return new ConstVariableValue(oA, false);
    if (oA instanceof Double)
      return new ConstVariableValue(oA, true);
    return null;
  }

  public static String filterQuotes(String string) {
    if(string == null)
      return null;
    while(string.startsWith("\"")&&string.endsWith("\""))
      string = string.substring(1,string.length()-1);
    return string;
  }

  private static Map<String, String> replaceMap = new HashMap<>();
  static{
    replaceMap.put("(", "#40;");
    replaceMap.put(")", "#41;");
    replaceMap.put(" ", "#32;");
    replaceMap.put("+", "#43;");
    replaceMap.put("*", "#42;");
    replaceMap.put("-", "#45;");
    replaceMap.put("&", "#38;");
    replaceMap.put("|", "#124;");
    replaceMap.put("<", "#60;");
    replaceMap.put(">", "#62;");
    replaceMap.put("=", "#61;");
    replaceMap.put("/", "#47;");
  }
  private static String replaceQuoteConstsBack(String str) {
    for(Entry<String, String> en: replaceMap.entrySet()) {
      str = str.replace(en.getValue(), en.getKey());
    }
    return str;
  }
  public static String reworkQuoteChars(String str) {
    StringBuilder sb = new StringBuilder();
    char selQuote = '"';
    int lastpos = 0, lastCut = 0;
    while (lastpos>-1){
      int pos1 = str.indexOf("'",lastpos);
      int pos2 = str.indexOf("\"", lastpos);
      lastpos = -1;
      int posNext = -1; 
      if(pos1>-1 && (pos1<pos2 || pos2==-1)) {
        selQuote = '\'';
        lastpos = pos1;
      }else if(pos2>-1){
        selQuote = '"';
        lastpos = pos2;
      }
      if(lastpos>-1){
        posNext = str.indexOf(selQuote, lastpos+1);
        if(posNext>-1){
          String constContains = str.substring(lastpos+1, posNext);
          sb.append(str.subSequence(lastCut, lastpos+1)); lastCut = lastpos+1;
          for(Entry<String, String> en: replaceMap.entrySet()){
            constContains = constContains.replace(en.getKey(), en.getValue());
          }
          sb.append(constContains); lastCut = posNext+1;
          sb.append(selQuote);
          lastpos = lastCut;
        }//else actions?
        if(lastCut<lastpos){
          sb.append(str.substring(lastCut, lastpos));
          lastCut = lastpos;
        }
      }
    }
    if(lastCut<str.length())
      sb.append(str.substring(lastCut,str.length()));
	return sb.toString();
  }
}
