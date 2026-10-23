package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;

/** {@code org.eclipse.ui.IWorkbenchWizard}. */
public interface IWorkbenchWizard extends com.unitesk.requality.vscode.ui.jface.wizard.IWizard {
  void init(IWorkbench workbench, IStructuredSelection selection);
}
