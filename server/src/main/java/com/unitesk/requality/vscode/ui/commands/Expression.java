package com.unitesk.requality.vscode.ui.commands;

import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.core.expressions.Expression}: a condition that decides whether a contribution is shown. */
public abstract class Expression {
  /** An expression that always evaluates to {@link EvaluationResult#TRUE}. */
  public static final Expression TRUE = new Expression() {
    @Override
    public EvaluationResult evaluate(IEvaluationContext context) {
      return EvaluationResult.TRUE;
    }
  };

  /** An expression that always evaluates to {@link EvaluationResult#FALSE}. */
  public static final Expression FALSE = new Expression() {
    @Override
    public EvaluationResult evaluate(IEvaluationContext context) {
      return EvaluationResult.FALSE;
    }
  };

  public abstract EvaluationResult evaluate(IEvaluationContext context) throws CoreException;
}
