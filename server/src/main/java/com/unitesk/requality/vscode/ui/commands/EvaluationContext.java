package com.unitesk.requality.vscode.ui.commands;

import java.util.HashMap;
import java.util.Map;

/** {@code org.eclipse.core.expressions.EvaluationContext}. */
public class EvaluationContext implements IEvaluationContext {
  private final IEvaluationContext parent;
  private final Object defaultVariable;
  private final Map<String, Object> variables = new HashMap<>();

  public EvaluationContext(IEvaluationContext parent, Object defaultVariable) {
    this.parent = parent;
    this.defaultVariable = defaultVariable;
  }

  @Override
  public Object getVariable(String name) {
    if (variables.containsKey(name))
      return variables.get(name);
    return parent != null ? parent.getVariable(name) : null;
  }

  @Override
  public void addVariable(String name, Object value) {
    variables.put(name, value);
  }

  @Override
  public Object removeVariable(String name) {
    return variables.remove(name);
  }

  @Override
  public Object getDefaultVariable() {
    return defaultVariable;
  }

  @Override
  public IEvaluationContext getParent() {
    return parent;
  }
}
