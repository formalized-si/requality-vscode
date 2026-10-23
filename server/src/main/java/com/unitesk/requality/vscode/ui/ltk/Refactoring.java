package com.unitesk.requality.vscode.ui.ltk;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/** {@code org.eclipse.ltk.core.refactoring.Refactoring}: the conditions checks and the change of a refactoring. */
public abstract class Refactoring {

  /** {@code org.eclipse.ltk.core.refactoring.Refactoring#getName()}: the name shown in the wizard and the undo labels. */
  public abstract String getName();

  /** {@code org.eclipse.ltk.core.refactoring.Refactoring#checkInitialConditions}. */
  public abstract RefactoringStatus checkInitialConditions(IProgressMonitor pm)
      throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.Refactoring#checkFinalConditions}. */
  public abstract RefactoringStatus checkFinalConditions(IProgressMonitor pm)
      throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.Refactoring#createChange}. */
  public abstract Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.Refactoring#dispose()}. */
  public void dispose() {
  }
}
