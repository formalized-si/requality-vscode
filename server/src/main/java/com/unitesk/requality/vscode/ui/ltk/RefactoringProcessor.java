package com.unitesk.requality.vscode.ui.ltk;

import com.unitesk.requality.vscode.ui.ltk.participants.CheckConditionsContext;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/** {@code org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor}: the work of a processor-based refactoring. */
public abstract class RefactoringProcessor {

  /** {@code org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getProcessorName()}. */
  public abstract String getProcessorName();

  /** {@code org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#isApplicable()}. */
  public abstract boolean isApplicable() throws CoreException;

  /** {@code org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkInitialConditions}. */
  public abstract RefactoringStatus checkInitialConditions(IProgressMonitor pm)
      throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkFinalConditions}. */
  public abstract RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
      throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#createChange}. */
  public abstract Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getElements()}. */
  public Object[] getElements() {
    return new Object[0];
  }
}
