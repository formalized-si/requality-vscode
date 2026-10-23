package com.unitesk.requality.vscode.ui.compare.patch;

import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;

/** {@code org.eclipse.core.runtime.SubMonitor.convert}: the progress of a sub task (the monitor itself, or none). */
final class SubMonitor {
  private SubMonitor() {
  }

  static IProgressMonitor convert(IProgressMonitor monitor, int ticks) {
    return monitor == null ? new NullProgressMonitor() : monitor;
  }
}
