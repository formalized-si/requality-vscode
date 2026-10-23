package com.unitesk.requality.vscode.ui.ltk;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.runtime.IStatus;

/** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus}. */
public class RefactoringStatus {
  public static final int OK = 0;
  public static final int INFO = 1;
  public static final int WARNING = 2;
  public static final int ERROR = 3;
  public static final int FATAL = 4;

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatusEntry}: one problem with its severity. */
  public static final class Entry {
    final int severity;
    final String message;

    Entry(int severity, String message) {
      this.severity = severity;
      this.message = message;
    }

    /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatusEntry#getSeverity()}. */
    public int getSeverity() {
      return severity;
    }

    /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatusEntry#getMessage()}. */
    public String getMessage() {
      return message;
    }

    /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatusEntry#isFatalError()}. */
    public boolean isFatalError() {
      return severity == FATAL;
    }
  }

  private final List<Entry> entries = new ArrayList<>();

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#createFatalErrorStatus(String)}. */
  public static RefactoringStatus createFatalErrorStatus(String msg) {
    RefactoringStatus result = new RefactoringStatus();
    result.addFatalError(msg);
    return result;
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#createErrorStatus(String)}. */
  public static RefactoringStatus createErrorStatus(String msg) {
    RefactoringStatus result = new RefactoringStatus();
    result.addError(msg);
    return result;
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#createWarningStatus(String)}. */
  public static RefactoringStatus createWarningStatus(String msg) {
    RefactoringStatus result = new RefactoringStatus();
    result.addWarning(msg);
    return result;
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#addInfo(String)}. */
  public void addInfo(String msg) {
    entries.add(new Entry(INFO, msg));
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#addWarning(String)}. */
  public void addWarning(String msg) {
    entries.add(new Entry(WARNING, msg));
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#addError(String)}. */
  public void addError(String msg) {
    entries.add(new Entry(ERROR, msg));
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#addFatalError(String)}. */
  public void addFatalError(String msg) {
    entries.add(new Entry(FATAL, msg));
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#getEntries()}: the problems in their order. */
  public Entry[] getEntries() {
    return entries.toArray(new Entry[0]);
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#create(IStatus)}. */
  public static RefactoringStatus create(IStatus status) {
    RefactoringStatus result = new RefactoringStatus();
    result.addStatus(status);
    return result;
  }

  private void addStatus(IStatus status) {
    if (status == null)
      return;
    if (status.isMultiStatus()) {
      for (IStatus child : status.getChildren())
        addStatus(child);
      return;
    }
    int severity = toSeverity(status.getSeverity());
    if (severity != OK)
      entries.add(new Entry(severity, status.getMessage()));
  }

  /** Maps {@code org.eclipse.core.runtime.IStatus} severities to {@code RefactoringStatus} severities. */
  private static int toSeverity(int status) {
    switch (status) {
      case IStatus.CANCEL:
        return FATAL;
      case IStatus.ERROR:
        return ERROR;
      case IStatus.WARNING:
        return WARNING;
      case IStatus.INFO:
        return INFO;
      default:
        return OK;
    }
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#isOK()}. */
  public boolean isOK() {
    return entries.isEmpty();
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#hasError()}. */
  public boolean hasError() {
    return getSeverity() >= ERROR;
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#hasFatalError()}. */
  public boolean hasFatalError() {
    return getSeverity() == FATAL;
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#getSeverity()}: the worst severity. */
  public int getSeverity() {
    int max = OK;
    for (Entry e : entries)
      max = Math.max(max, e.severity);
    return max;
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#merge(RefactoringStatus)}. */
  public void merge(RefactoringStatus other) {
    if (other != null)
      entries.addAll(other.entries);
  }

  /** {@code org.eclipse.ltk.core.refactoring.RefactoringStatus#getMessageMatchingSeverity(int)}. */
  public String getMessageMatchingSeverity(int severity) {
    for (Entry e : entries)
      if (e.severity == severity)
        return e.message;
    return null;
  }
}
