package com.unitesk.requality.vscode.ui.ltk;

import com.unitesk.requality.vscode.ui.ltk.resource.RenameResourceDescriptor;

/** {@code org.eclipse.ltk.core.refactoring.RefactoringCore}: the refactorings contributed to the workbench. */
public final class RefactoringCore {
  private RefactoringCore() {
  }

  /** The contribution of the refactoring id, or null when none is contributed. */
  public static RefactoringContribution getRefactoringContribution(String id) {
    if (RenameResourceDescriptor.ID.equals(id)) {
      return new RefactoringContribution() {
        @Override
        public RefactoringDescriptor createDescriptor() {
          return new RenameResourceDescriptor();
        }
      };
    }
    return null;
  }
}
