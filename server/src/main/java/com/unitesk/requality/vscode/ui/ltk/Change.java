package com.unitesk.requality.vscode.ui.ltk;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/** {@code org.eclipse.ltk.core.refactoring.Change}. */
public abstract class Change {

  /** {@code org.eclipse.ltk.core.refactoring.Change#getName()}. */
  public abstract String getName();

  /** {@code org.eclipse.ltk.core.refactoring.Change#initializeValidationData(IProgressMonitor)}. */
  public abstract void initializeValidationData(IProgressMonitor pm);

  /** {@code org.eclipse.ltk.core.refactoring.Change#isValid(IProgressMonitor)}. */
  public abstract RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.Change#perform(IProgressMonitor)}: returns the undo change. */
  public abstract Change perform(IProgressMonitor pm) throws CoreException;

  /** {@code org.eclipse.ltk.core.refactoring.Change#getModifiedElement()}. */
  public abstract Object getModifiedElement();

  private boolean fIsEnabled = true;

  /** {@code Change#isEnabled()}: a disabled change (unchecked in the preview) is not performed. */
  public boolean isEnabled() {
    return fIsEnabled;
  }

  /** {@code Change#setEnabled(boolean)}. */
  public void setEnabled(boolean enabled) {
    fIsEnabled = enabled;
  }

  /** {@code org.eclipse.ltk.core.refactoring.Change#dispose()}. */
  public void dispose() {
  }
}
