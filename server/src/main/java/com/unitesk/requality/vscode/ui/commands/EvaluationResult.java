package com.unitesk.requality.vscode.ui.commands;

/** {@code org.eclipse.core.expressions.EvaluationResult}: the outcome of evaluating an {@link Expression}. */
public final class EvaluationResult {
  public static final EvaluationResult TRUE = new EvaluationResult("TRUE");
  public static final EvaluationResult FALSE = new EvaluationResult("FALSE");
  public static final EvaluationResult NOT_LOADED = new EvaluationResult("NOT_LOADED");

  private final String name;

  private EvaluationResult(String name) {
    this.name = name;
  }

  public static EvaluationResult valueOf(boolean value) {
    return value ? TRUE : FALSE;
  }

  @Override
  public String toString() {
    return name;
  }
}
