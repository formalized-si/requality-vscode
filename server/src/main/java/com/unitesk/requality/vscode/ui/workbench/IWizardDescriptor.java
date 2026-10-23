package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.ui.wizards.IWizardDescriptor}: a wizard declared by an extension point of the plug-in. */
public interface IWizardDescriptor {
  String getId();

  String getLabel();

  /** The {@code description} element of the wizard, or null. */
  String getDescription();

  /** The category the wizard is declared in, or null if it is declared outside any category. */
  IWizardCategory getCategory();

  /** The tags of the wizard: {@code project} for the wizards with {@code project="true"}. */
  String[] getTags();

  /** Instantiates the wizard declared by the {@code class} attribute. */
  IWizard createWizard() throws CoreException;
}
