package com.unitesk.requality.vscode.ui.runtime;

import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/** {@code org.eclipse.ui.progress.UIJob}: runs on the remote SWT UI thread. */
public abstract class UIJob extends Job {
  public UIJob(String name) {
    super(name);
    setSystem(true);
  }

  public UIJob(Display display, String name) {
    this(name);
  }

  public abstract IStatus runInUIThread(IProgressMonitor monitor);

  @Override
  protected final IStatus run(IProgressMonitor monitor) {
    return runInUIThread(monitor);
  }

  @Override
  protected void execute() {
    // the display runs it as work of the thread that scheduled the job
    Display.getDefault().asyncExec(scheduledFrom, super::execute);
  }

  public Display getDisplay() {
    return Display.getDefault();
  }
}
