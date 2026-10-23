package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.resources.BuildManager;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IncrementalProjectBuilder;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.CleanDialog} (Project > Clean...): cleans all projects or the projects
 * checked below (their builders discard what they built), then builds: when the workspace builds automatically the
 * cleaned projects are rebuilt from scratch, otherwise only if "Start a build immediately" is checked.
 */
public class CleanDialog extends Dialog {
  private final IProject[] selection;
  private Button allButton, selectedButton, buildNowButton, globalBuildButton, projectsBuildButton;
  private CheckboxTableViewer projectNames;
  private Object[] checked;
  private Job job;

  public CleanDialog(Shell shell, IProject[] selection) {
    super(shell);
    this.selection = selection == null ? new IProject[0] : selection;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Clean");
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "Clean", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite) super.createDialogArea(parent);
    boolean autoBuilding = ResourcesPlugin.getWorkspace().isAutoBuilding();
    Label question = new Label(area, SWT.WRAP);
    question.setText(autoBuilding
        ? "Clean will discard all build problems and built states.  The projects will be rebuilt from scratch."
        : "Clean will discard all build problems and built states.  The next time a build occurs the projects will be rebuilt from scratch.");
    question.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    SelectionAdapter updateEnablement = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateEnablement();
      }
    };
    allButton = new Button(area, SWT.RADIO);
    allButton.setText("Clean &all projects");
    allButton.setSelection(selection.length == 0);
    allButton.addSelectionListener(updateEnablement);
    selectedButton = new Button(area, SWT.RADIO);
    selectedButton.setText("Clean projects &selected below");
    selectedButton.setSelection(selection.length > 0);
    selectedButton.addSelectionListener(updateEnablement);
    projectNames = CheckboxTableViewer.newCheckList(area, SWT.BORDER);
    projectNames.setContentProvider(new ArrayContentProvider());
    projectNames.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return ((IProject) element).getName();
      }
    });
    GridData data = new GridData(GridData.FILL_BOTH);
    data.heightHint = 150;
    projectNames.getTable().setLayoutData(data);
    projectNames.setInput(BuildManager.openProjects().toArray());
    projectNames.setCheckedElements(selection);
    projectNames.addCheckStateListener(event -> {
      selectedButton.setSelection(true);
      allButton.setSelection(false);
      updateEnablement();
    });
    // only prompt for immediate build if autobuild is off
    if (!autoBuilding) {
      buildNowButton = new Button(area, SWT.CHECK);
      buildNowButton.setText("Start a &build immediately");
      buildNowButton.setSelection(true);
      buildNowButton.addSelectionListener(updateEnablement);
      globalBuildButton = new Button(area, SWT.RADIO);
      globalBuildButton.setText("Build the &entire workspace");
      globalBuildButton.setSelection(true);
      projectsBuildButton = new Button(area, SWT.RADIO);
      projectsBuildButton.setText("Build only the selected &projects");
    }
    return area;
  }

  @Override
  public void create() {
    super.create();
    updateEnablement();
  }

  private void updateEnablement() {
    boolean cleanAll = allButton.getSelection();
    boolean enabled = cleanAll || projectNames.getCheckedElements().length > 0;
    if (getButton(IDialogConstants.OK_ID) != null)
      getButton(IDialogConstants.OK_ID).setEnabled(enabled);
    if (globalBuildButton != null) {
      globalBuildButton.setEnabled(buildNowButton.getSelection());
      projectsBuildButton.setEnabled(buildNowButton.getSelection() && !cleanAll);
      if (cleanAll && projectsBuildButton.getSelection()) {
        projectsBuildButton.setSelection(false);
        globalBuildButton.setSelection(true);
      }
    }
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == IDialogConstants.OK_ID) {
      final boolean cleanAll = allButton.getSelection();
      final boolean buildNow = buildNowButton != null && buildNowButton.getSelection();
      final boolean buildAll = buildNow && globalBuildButton.getSelection();
      checked = projectNames.getCheckedElements();
      final List<IProject> projects = new ArrayList<>();
      for (Object o : cleanAll ? BuildManager.openProjects().toArray() : checked)
        projects.add((IProject) o);
      job = new Job(cleanAll ? "Cleaning all projects" : "Cleaning selected projects") {
        @Override
        protected IStatus run(IProgressMonitor monitor) {
          try {
            BuildManager.build(projects, IncrementalProjectBuilder.CLEAN_BUILD, monitor);
            if (ResourcesPlugin.getWorkspace().isAutoBuilding())
              // the auto-build after the clean: the cleaned projects have no build state, they are fully rebuilt
              BuildManager.build(projects, IncrementalProjectBuilder.AUTO_BUILD, monitor);
            else if (buildNow)
              BuildManager.build(buildAll ? null : projects, IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
            return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
          } catch (CoreException e) {
            return e.getStatus();
          }
        }
      };
      job.setUser(true);
      job.schedule();
    }
    super.buttonPressed(buttonId);
  }

  /** The clean job started by the Clean button (tests). */
  public Job getJob() {
    return job;
  }

  /** The projects selected at the start. */
  public List<IProject> getInitialSelection() {
    return Arrays.asList(selection);
  }
}
