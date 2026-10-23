package com.unitesk.requality.vscode.ui.ltk;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/** {@code org.eclipse.ltk.core.refactoring.CompositeChange}. */
public class CompositeChange extends Change {
  private final String name;
  private final List<Change> children = new ArrayList<>();

  /** {@code org.eclipse.ltk.core.refactoring.CompositeChange#CompositeChange(String)}. */
  public CompositeChange(String name) {
    this(name, new Change[0]);
  }

  /** {@code org.eclipse.ltk.core.refactoring.CompositeChange#CompositeChange(String, Change[])}. */
  public CompositeChange(String name, Change[] children) {
    this.name = name;
    if (children != null)
      for (Change c : children)
        add(c);
  }

  /** {@code org.eclipse.ltk.core.refactoring.CompositeChange#add(Change)}. */
  public void add(Change change) {
    if (change != null)
      children.add(change);
  }

  /** {@code org.eclipse.ltk.core.refactoring.CompositeChange#getChildren()}. */
  public Change[] getChildren() {
    return children.toArray(new Change[0]);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void initializeValidationData(IProgressMonitor pm) {
    for (Change c : children)
      c.initializeValidationData(pm);
  }

  @Override
  public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    RefactoringStatus status = new RefactoringStatus();
    for (Change c : children)
      status.merge(c.isValid(pm));
    return status;
  }

  /**
   * {@code org.eclipse.ltk.core.refactoring.CompositeChange#perform(IProgressMonitor)}: performs the children and
   * returns their undo; when a child fails, the undo of the children already performed is executed (Eclipse
   * handleUndos) and the failure is rethrown.
   */
  @Override
  public Change perform(IProgressMonitor pm) throws CoreException {
    List<Change> undos = new ArrayList<>();
    for (Change c : children) {
      try {
        Change undo = c.perform(pm);
        if (undo != null)
          undos.add(0, undo);
      } catch (CoreException | RuntimeException e) {
        for (Change u : undos)
          try {
            u.perform(pm);
          } catch (CoreException | RuntimeException x) {
            com.unitesk.requality.core.log.RLogger.logError(x);
          }
        throw e;
      }
    }
    return new CompositeChange(name, undos.toArray(new Change[0]));
  }

  @Override
  public Object getModifiedElement() {
    return children.isEmpty() ? null : children.get(0).getModifiedElement();
  }

  @Override
  public void dispose() {
    for (Change c : children)
      c.dispose();
  }
}
