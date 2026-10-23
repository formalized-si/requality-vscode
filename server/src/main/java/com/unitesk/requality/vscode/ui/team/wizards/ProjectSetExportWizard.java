package com.unitesk.requality.vscode.ui.team.wizards;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.workbench.IExportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ProjectSetExportWizard}: File &gt; Export &gt; Team &gt; Team Project Set, the
 * wizard that writes the projects with their repository providers to a Team Project Set file.
 * VSCODE: no repository provider is installed, so no provider element is written.
 */
public class ProjectSetExportWizard extends Wizard implements IExportWizard {
  private ExportProjectSetMainPage mainPage;
  private ExportProjectSetLocationPage locationPage;
  private IStructuredSelection selection;

  public ProjectSetExportWizard() {
    setNeedsProgressMonitor(true);
    setWindowTitle("Team Project Set");
  }

  @Override
  public void addPages() {
    mainPage = new ExportProjectSetMainPage("projectSetMainPage", "Export a Team Project Set", null);
    List<IProject> projects = new ArrayList<>();
    if (selection != null)
      for (Object o : selection.toList())
        if (o instanceof IProject)
          projects.add((IProject) o);
    mainPage.setSelectedProjects(projects.toArray(new IProject[0]));
    addPage(mainPage);
    locationPage = new ExportProjectSetLocationPage("projectSetLocationPage", "Export a Team Project Set", null);
    addPage(locationPage);
  }

  @Override
  public boolean performFinish() {
    String filename = locationPage.getFileName();
    if (new File(filename).getName().indexOf('.') < 0)
      filename = filename + ".psf";
    PsfHistory.remember(PsfHistory.FILENAMES, filename);
    File file = new File(filename);
    File parentFile = file.getParentFile();
    if (parentFile != null && !parentFile.exists()) {
      if (!MessageDialog.openQuestion(getShell(), "Question",
          "Target directory does not exist. Would you like to create it?"))
        return false;
      if (!parentFile.mkdirs()) {
        MessageDialog.openError(getShell(), "Export Problems", "An error occurred creating the target directory");
        return false;
      }
    }
    if (file.isFile() && !MessageDialog.openQuestion(getShell(), "Question",
        "Target already exists. Would you like to overwrite it?"))
      return false;
    try {
      Files.writeString(file.toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<psf version=\"2.0\"/>\n",
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    this.selection = selection;
  }
}
