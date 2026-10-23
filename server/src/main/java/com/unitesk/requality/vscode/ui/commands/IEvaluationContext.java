package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.core.expressions.IEvaluationContext}: the variables of a command execution. */
public interface IEvaluationContext {
  Object UNDEFINED_VARIABLE = new Object();

  Object getVariable(String name);

  void addVariable(String name, Object value);

  Object removeVariable(String name);

  Object getDefaultVariable();

  IEvaluationContext getParent();
}
