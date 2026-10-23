package com.unitesk.requality.vscode.ui.team.ui;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/** {@code org.eclipse.team.ui.IConfigurationWizardExtension}: a configuration wizard that shares several projects. */
public interface IConfigurationWizardExtension {
  void init(IWorkbench workbench, IProject[] projects);
}
