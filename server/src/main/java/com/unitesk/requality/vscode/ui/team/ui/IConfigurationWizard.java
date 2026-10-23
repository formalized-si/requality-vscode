package com.unitesk.requality.vscode.ui.team.ui;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.team.ui.IConfigurationWizard}: the wizard of a repository type of Share Project (the
 * {@code configurationWizards} extension point of org.eclipse.team.ui), initialized with the project to share.
 */
public interface IConfigurationWizard extends IWizard {
  void init(IWorkbench workbench, IProject project);
}
