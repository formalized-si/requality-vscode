package com.unitesk.requality.vscode.ui.ltk.participants;

/** {@code org.eclipse.ltk.core.refactoring.participants.RenameArguments}. */
public class RenameArguments extends RefactoringArguments {
  private final String newName;
  private final boolean updateReferences;

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameArguments#RenameArguments(String, boolean)}. */
  public RenameArguments(String newName, boolean updateReferences) {
    this.newName = newName;
    this.updateReferences = updateReferences;
  }

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameArguments#getNewName()}. */
  public String getNewName() {
    return newName;
  }

  /** {@code org.eclipse.ltk.core.refactoring.participants.RenameArguments#getUpdateReferences()}. */
  public boolean getUpdateReferences() {
    return updateReferences;
  }
}
