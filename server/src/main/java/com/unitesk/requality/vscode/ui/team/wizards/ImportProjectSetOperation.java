package com.unitesk.requality.vscode.ui.team.wizards;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableContext;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.ProjectSetImporter;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ImportProjectSetOperation}: imports a Team Project Set from a file or from
 * the contents of a URL, and adds the imported projects to the working sets. With no runnable context the import runs in
 * a background job, errors are then shown in a dialog.
 */
public class ImportProjectSetOperation {
  private static final String PLUGIN_ID = "org.eclipse.team.ui";
  private static final String JOB_NAME = "Importing project set...";

  private final IRunnableContext context;
  private final String psfFileContents;
  private final String psfFile;
  private final IWorkingSet[] workingSets;
  private final Shell shell;

  /** The import of a Team Project Set stored in a string (the content loaded from the URL). */
  public ImportProjectSetOperation(IRunnableContext context, String psfFileContents, String urlString,
      IWorkingSet[] workingSets) {
    this.context = context;
    this.psfFileContents = psfFileContents;
    this.psfFile = null;
    this.workingSets = workingSets;
    this.shell = Display.getDefault().getActiveShell();
  }

  /** The import of a Team Project Set file. */
  public ImportProjectSetOperation(IRunnableContext context, String psfFile, IWorkingSet[] workingSets) {
    this.context = context;
    this.psfFileContents = null;
    this.psfFile = psfFile;
    this.workingSets = workingSets;
    this.shell = Display.getDefault().getActiveShell();
  }

  /** {@code ImportProjectSetOperation.run}: in the context, or in a background job when the context is null. */
  public void run() throws InvocationTargetException, InterruptedException {
    if (context == null) {
      Job job = new Job(JOB_NAME) {
        @Override
        protected IStatus run(IProgressMonitor monitor) {
          try {
            ImportProjectSetOperation.this.execute(monitor);
          } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            IStatus status = new Status(IStatus.ERROR, PLUGIN_ID, 0,
                String.valueOf(target == null ? "" : target.getMessage()), target);
            Display.getDefault().asyncExec(() -> ErrorDialog.openError(shell, null, null, status));
            return status;
          }
          return Status.OK_STATUS;
        }
      };
      job.setUser(true);
      job.schedule();
      return;
    }
    context.run(true, true, monitor -> execute(monitor));
  }

  /** The import itself: the projects of the file, then the working sets. */
  void execute(IProgressMonitor monitor) throws InvocationTargetException {
    File temp = null;
    try {
      String filename = psfFile;
      if (psfFileContents != null) {
        temp = File.createTempFile("psf", ".psf");
        Files.writeString(temp.toPath(), psfFileContents, StandardCharsets.UTF_8);
        filename = temp.getAbsolutePath();
      } else {
        PsfHistory.remember(PsfHistory.FILENAMES, filename);
      }
      IProject[] newProjects = ProjectSetImporter.importProjectSet(filename, shell, monitor);
      createWorkingSets(newProjects);
    } catch (IOException e) {
      throw new InvocationTargetException(e);
    } finally {
      if (temp != null)
        temp.delete();
    }
  }

  /** The projects go into each working set: a new set is created, an existing one keeps its elements. */
  private void createWorkingSets(IProject[] projects) {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    for (IWorkingSet workingSet : workingSets) {
      String name = workingSet.getName();
      IWorkingSet oldSet = manager.getWorkingSet(name);
      if (oldSet == null) {
        manager.addWorkingSet(manager.createWorkingSet(name, projects));
      } else {
        IAdaptable[] existing = oldSet.getElements();
        IAdaptable[] adapted = oldSet.adaptElements(projects);
        IAdaptable[] all = new IAdaptable[existing.length + adapted.length];
        System.arraycopy(existing, 0, all, 0, existing.length);
        System.arraycopy(adapted, 0, all, existing.length, adapted.length);
        oldSet.setElements(all);
      }
    }
  }
}
