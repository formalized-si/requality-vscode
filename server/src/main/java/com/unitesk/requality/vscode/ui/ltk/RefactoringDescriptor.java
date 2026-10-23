package com.unitesk.requality.vscode.ui.ltk;

import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.ltk.core.refactoring.RefactoringDescriptor}: a refactoring stored by its arguments. */
public abstract class RefactoringDescriptor {
  /** Creates the refactoring of the arguments; the status collects the problems of the arguments. */
  public abstract Refactoring createRefactoring(RefactoringStatus status) throws CoreException;
}
