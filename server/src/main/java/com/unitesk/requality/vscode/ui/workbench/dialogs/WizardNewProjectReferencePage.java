package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code org.eclipse.ui.dialogs.WizardNewProjectReferencePage}: the page that lists the open projects of the workspace
 * so that the user can check the projects the new project references.
 */
public class WizardNewProjectReferencePage extends WizardPage {
  private CheckboxTableViewer listViewer;

  /** @param pageName the name of the page */
  public WizardNewProjectReferencePage(String pageName) {
    super(pageName);
    setPageComplete(true);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    listViewer = CheckboxTableViewer.newCheckList(composite, SWT.TOP | SWT.BORDER);
    listViewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
    listViewer.setContentProvider(new ArrayContentProvider());
    listViewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return ((IProject) element).getName();
      }
    });
    List<IProject> open = new ArrayList<>();
    for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      if (p.isOpen())
        open.add(p);
    listViewer.setInput(open.toArray(new IProject[0]));
    setControl(composite);
  }

  /** The projects checked by the user (the projects the new project references). */
  public IProject[] getReferencedProjects() {
    if (listViewer == null)
      return new IProject[0];
    Object[] checked = listViewer.getCheckedElements();
    IProject[] r = new IProject[checked.length];
    for (int i = 0; i < checked.length; i++)
      r[i] = (IProject) checked[i];
    return r;
  }
}
