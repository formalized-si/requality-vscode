package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyPage;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.ProjectReferencePage}: the projects the project references, as a check
 * list of the other projects of the workspace. The references are stored in the {@code <projects>} element of the
 * {@code .project} file (the dynamic references are not listed).
 */
public class ProjectReferencePage extends PropertyPage {
  private IProject project;
  private boolean modified = false;
  private CheckboxTableViewer listViewer;

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    initialize();

    listViewer = CheckboxTableViewer.newCheckList(composite, SWT.TOP | SWT.BORDER);
    if (!project.isOpen())
      listViewer.getControl().setEnabled(false);
    listViewer.setLabelProvider(WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider());
    listViewer.setContentProvider(ArrayContentProvider.getInstance());
    listViewer.setComparator(new ViewerComparator());
    listViewer.setInput(candidates(project));
    try {
      listViewer.setCheckedElements(project.getDescription().getReferencedProjects());
    } catch (CoreException e) {
      // no references are checked
    }
    listViewer.addCheckStateListener(event -> modified = true);

    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    GridData data = new GridData(GridData.FILL_BOTH);
    listViewer.getControl().setLayoutData(data);
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    return composite;
  }

  /** The other projects of the workspace, then the referenced projects that are not in the workspace. */
  static Object[] candidates(IProject project) {
    List<IProject> result = new ArrayList<>();
    for (IProject current : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      if (!current.equals(project) && isProject(current))
        result.add(current);
    try {
      for (IProject referenced : project.getDescription().getReferencedProjects())
        if (!result.contains(referenced))
          result.add(referenced);
    } catch (CoreException e) {
      // only the workspace projects are listed
    }
    return result.toArray();
  }

  /** A workspace folder without a .project file is not an Eclipse project. */
  private static boolean isProject(IProject p) {
    return p instanceof ProjectRef && new File(((ProjectRef) p).toFile(), ".project").isFile();
  }

  private void initialize() {
    IResource resource = ResourceInfoPage.resourceOf(getElement());
    project = resource == null ? null : resource.getProject();
    noDefaultAndApplyButton();
    setDescription("Projects may refer to other projects in the workspace.\n"
        + "Use this page to specify what other projects are referenced by the project.\n\n"
        + "Project references for '" + project.getName() + "':");
  }

  @Override
  public boolean performOk() {
    if (!modified)
      return true;
    Object[] checked = listViewer.getCheckedElements();
    IProject[] refs = new IProject[checked.length];
    System.arraycopy(checked, 0, refs, 0, checked.length);
    try {
      IProjectDescription description = project.getDescription();
      description.setReferencedProjects(refs);
      project.setDescription(description, null);
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), null, null, e.getStatus());
      return false;
    }
    return true;
  }
}
