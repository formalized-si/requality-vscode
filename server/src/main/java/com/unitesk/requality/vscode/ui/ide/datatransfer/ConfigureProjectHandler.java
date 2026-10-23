package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.ConfigureProjectHandler}: opens the {@link SmartImportWizard} on the
 * folder of the selected project ("Configure and Detect Nested Projects...").
 */
public class ConfigureProjectHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent arg0) {
    IProject project = null;
    IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(arg0);
    if (selection != null) {
      Object item = selection.getFirstElement();
      project = Adapters.adapt(item, IProject.class);
    }
    if (project == null) {
      return null;
    }
    File file = SmartImportWizard.toFile(project);
    if (file == null) {
      return null;
    }

    SmartImportWizard wizard = new SmartImportWizard();
    wizard.setInitialImportSource(file);
    // inherit workingSets
    Set<IWorkingSet> workingSets = new HashSet<>();
    for (IWorkingSet workingSet : PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets()) {
      for (IAdaptable element : workingSet.getElements()) {
        if (project.getAdapter(element.getClass()) == element) {
          workingSets.add(workingSet);
        }
      }
    }
    wizard.setInitialWorkingSets(workingSets);
    return new WizardDialog(HandlerUtil.getActiveShell(arg0), wizard).open();
  }
}
