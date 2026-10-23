package com.unitesk.requality.report.template;

public enum ConditionOp {
//“==”, “<”, “<=”, “>”, “>=” - в этом случае тип “op1” - имя атрибута (id если решим их вводить), тип - str. Если тип этого атрибута - LIST, то в условии будет фигурировать длина списка (например, условие {“op1”: “list1Attr”, “opd”: “>”, “op2”: 10} будет true если длина значения атрибута “list1Attr” будет превышать 10). Если тип атрибута - STRING/BOOL/HTML, то будет работать только первый операнд, остальные приведут к отключению атрибута.
	EQ, LESS, LESSEQ, MORE,MOREEQ,
//			“==$P”, “<$P”, “<=$P”, “>$P”, “>=$P” - для этих операндов “op1” и “op2” - имена атрибутов, и в условии будут фигурировать их значения. Для LIST, аналогично, будут фигурировать длины значений этих атрибутов. Для ошибочных условий (несовпадение типов, неподдерживаемых операндов) будет отключение атрибута.
	EQP,LESSP,LESSEQP,MOREP,MOREEQP,
//			“OR”, “AND” - в этом случае типы “op1” и “op2” - Condition.
	OR, AND,
			//“NOT” - “op2” для этого операнда отсутствует, тип “op1” - Condition.
	NOT;
	public static ConditionOp read(String val){
		switch(val.toUpperCase()) {
			case "==":return EQ;
			case "<":return LESS;
			case "<=":return LESSEQ;
			case ">":return MORE;
			case ">=":return MOREEQ;
			
			case "==$P":return EQP;
			case "<$P":return LESSP;
			case "<=$P":return LESSEQP;
			case ">$P":return MOREP;
			case ">=$P":return MOREEQP;
			
			case "AND": return AND;
			case "OR": return OR;
			case "NOT": return NOT;
		}
		return null;
	}
	@Override 
	public String toString(){
		switch(this){	
			case EQ:return "==";
			case LESS:return "<";
			case LESSEQ:return "<=";
			case MORE:return ">";
			case MOREEQ:return ">=";
			case EQP:return "==$P";
			case LESSP:return "<$P";
			case LESSEQP:return "<=$P";
			case MOREP:return ">$P";
			case MOREEQP:return ">=$P";
			case AND: return "AND";			
			case OR: return "OR";
			case NOT: return "NOT";
		}
		return null;
	};
	

}
