package com.unitesk.requality.vscode.ui.team;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.WorkbenchException;
import com.unitesk.requality.vscode.ui.workbench.XMLMemento;

/**
 * {@code org.eclipse.team.internal.ui.ProjectSetImporter}: imports a Team Project Set (.psf) file. Each provider element
 * names a repository type; a type that cannot be found is an error that is collected while the other providers are
 * imported, then reported together. The working sets of the file are added to the workbench.
 * VSCODE: the version 1.0 project sets (the versionOneSerializer of a team plug-in) and the aliases of repository types
 * are not ported.
 */
public class ProjectSetImporter {
  private ProjectSetImporter() {
  }

  /** {@code ProjectSetImporter.importProjectSet}: imports the project set file, returns the new projects. */
  public static IProject[] importProjectSet(String filename, Shell shell, IProgressMonitor monitor)
      throws InvocationTargetException {
    XMLMemento xmlMemento = filenameToXMLMemento(filename);
    try {
      List<IProject> newProjects = new ArrayList<>();
      List<IStatus> errors = new ArrayList<>();
      for (IMemento provider : xmlMemento.getChildren("provider")) {
        List<String> referenceStrings = new ArrayList<>();
        for (IMemento project : provider.getChildren("project"))
          referenceStrings.add(project.getString("reference"));
        String id = provider.getString("id");
        try {
          RepositoryProviderType providerType = RepositoryProviderType.getProviderType(id);
          if (providerType == null) {
            throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.team.ui", 0,
                MessageFormat.format("Projects for repository type {0} could not be loaded as the type could not be found.", id),
                null));
          }
          // VSCODE: the project set capability of the provider type is not ported: it adds no projects.
        } catch (CoreException e) {
          errors.add(e.getStatus());
        }
      }
      if (!errors.isEmpty()) {
        throw new CoreException(new MultiStatus("org.eclipse.team.ui", 0, errors.toArray(new IStatus[0]),
            "The following errors occurred while importing projects. Some projects may not be loaded.", null));
      }
      importWorkingSets(xmlMemento, shell);
      return newProjects.toArray(new IProject[0]);
    } catch (CoreException e) {
      throw new InvocationTargetException(e);
    }
  }

  /** The working sets of the file: a set that exists is replaced, merged or skipped as the user answers. */
  private static void importWorkingSets(XMLMemento xmlMemento, Shell shell) throws CoreException {
    IMemento[] sets = xmlMemento.getChildren("workingSets");
    if (sets.length == 0)
      return;
    IWorkingSetManager wsManager = Workbench.INSTANCE.getWorkingSetManager();
    boolean replaceAll = false;
    boolean mergeAll = false;
    boolean skipAll = false;
    for (IMemento set : sets) {
      IWorkingSet newWs = wsManager.createWorkingSet(set);
      if (newWs == null)
        continue;
      IWorkingSet oldWs = wsManager.getWorkingSet(newWs.getName());
      if (oldWs == null) {
        wsManager.addWorkingSet(newWs);
      } else if (replaceAll) {
        replaceWorkingSet(wsManager, newWs, oldWs);
      } else if (mergeAll) {
        mergeWorkingSets(newWs, oldWs);
      } else if (!skipAll) {
        // a working set with the same name has been found
        String title = "Working Set Exists";
        String msg = MessageFormat.format("Working Set ''{0}'' already exists. What would you like to do?", newWs.getName());
        String[] buttons = new String[] { "&Replace", "&Merge", "&Skip", IDialogConstants.CANCEL_LABEL };
        AdviceDialog dialog = new AdviceDialog(shell, title, msg, buttons);
        dialog.open();
        switch (dialog.getReturnCode()) {
          case 0: // overwrite
            replaceWorkingSet(wsManager, newWs, oldWs);
            replaceAll = dialog.applyToAll;
            break;
          case 1: // combine
            mergeWorkingSets(newWs, oldWs);
            mergeAll = dialog.applyToAll;
            break;
          case 2: // skip
            skipAll = dialog.applyToAll;
            break;
          case 3: // cancel
          default:
            throw new OperationCanceledException();
        }
      }
    }
  }

  private static XMLMemento filenameToXMLMemento(String filename) throws InvocationTargetException {
    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8)) {
      return XMLMemento.createReadRoot(reader);
    } catch (IOException | WorkbenchException e) {
      throw new InvocationTargetException(e);
    }
  }

  private static void mergeWorkingSets(IWorkingSet newWs, IWorkingSet oldWs) {
    Set<Object> combinedElements = new HashSet<>();
    combinedElements.addAll(List.of(oldWs.getElements()));
    combinedElements.addAll(List.of(newWs.getElements()));
    oldWs.setElements(combinedElements.toArray(new IAdaptable[0]));
  }

  private static void replaceWorkingSet(IWorkingSetManager wsManager, IWorkingSet newWs, IWorkingSet oldWs) {
    if (oldWs != null)
      wsManager.removeWorkingSet(oldWs);
    wsManager.addWorkingSet(newWs);
  }

  /** {@code ProjectSetImporter.AdviceDialog}: the question about a working set of the same name. */
  private static class AdviceDialog extends MessageDialog {
    boolean applyToAll;

    AdviceDialog(Shell parentShell, String dialogTitle, String dialogMessage, String[] dialogButtonLabels) {
      super(parentShell, dialogTitle, null, dialogMessage, MessageDialog.QUESTION, dialogButtonLabels, 0);
    }

    @Override
    protected Control createCustomArea(Composite parent) {
      final Button checkBox = new Button(parent, SWT.CHECK);
      checkBox.setText("&Apply to all working sets in this import");
      checkBox.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          applyToAll = checkBox.getSelection();
        }
      });
      return checkBox;
    }
  }
}
