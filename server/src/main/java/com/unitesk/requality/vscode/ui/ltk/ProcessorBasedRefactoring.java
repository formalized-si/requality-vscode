package com.unitesk.requality.vscode.ui.ltk;

import com.unitesk.requality.vscode.ui.ltk.participants.CheckConditionsContext;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/**
 * {@code org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring}: a refactoring that delegates its
 * conditions checks and its change to a {@link RefactoringProcessor}.
 */
public class ProcessorBasedRefactoring extends Refactoring {
  private final RefactoringProcessor processor;

  public ProcessorBasedRefactoring(RefactoringProcessor processor) {
    this.processor = processor;
  }

  /** {@code org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring#getProcessor()}. */
  public RefactoringProcessor getProcessor() {
    return processor;
  }

  @Override
  public String getName() {
    return processor.getProcessorName();
  }

  @Override
  public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
      throws CoreException, OperationCanceledException {
    return processor.checkInitialConditions(pm);
  }

  @Override
  public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return processor.checkFinalConditions(pm, new CheckConditionsContext());
  }

  @Override
  public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return processor.createChange(pm);
  }
}
