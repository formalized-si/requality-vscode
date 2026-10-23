package com.unitesk.requality.vscode.ui.ltk.participants;

import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/** {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant}. */
public abstract class RenameParticipant {
  private RenameArguments arguments;

  /**
   * {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant#initialize(Object)}. Eclipse passes the
   * {@code RefactoringProcessor} in the three-argument overload; here it is typed as {@code Object}.
   */
  protected abstract boolean initialize(Object element);

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant#getName()}. */
  public abstract String getName();

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant#checkConditions}. */
  public abstract RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
      throws OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant#createChange}. */
  public abstract Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException;

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant#createPreChange}: none by default. */
  public Change createPreChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return null;
  }

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant#getArguments()}. */
  public RenameArguments getArguments() {
    return arguments;
  }

  /**
   * Called by the server: stores the rename arguments, then initializes the participant with the element.
   */
  public boolean initialize(Object element, RenameArguments arguments) {
    this.arguments = arguments;
    return initialize(element);
  }

  /**
   * {@code org.eclipse.ltk.core.refactoring.participants.RenameParticipant#initialize(RefactoringProcessor, Object,
   * RefactoringArguments)}; the processor is typed as {@code Object}.
   */
  protected boolean initialize(Object processor, Object element, RefactoringArguments arguments) {
    if (arguments instanceof RenameArguments)
      this.arguments = (RenameArguments) arguments;
    return initialize(element);
  }
}
