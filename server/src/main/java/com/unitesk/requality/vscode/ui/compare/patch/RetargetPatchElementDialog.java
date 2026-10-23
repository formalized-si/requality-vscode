/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.compare.patch;

import java.util.ArrayList;

import com.unitesk.requality.vscode.ui.compare.patch.core.DiffProject;
import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.compare.patch.core.Hunk;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.navigator.ResourceComparator;
import com.unitesk.requality.vscode.ui.workbench.model.BaseWorkbenchContentProvider;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.compare.internal.patch.RetargetPatchElementDialog}: the dialog that moves a project, a file or a hunk
 * of the patch to another resource of the workspace (the target of the patch element).
 */
class RetargetPatchElementDialog extends Dialog {

  private static class RetargetPatchContentProvider extends BaseWorkbenchContentProvider {
    private final PatchDiffNode node;

    public RetargetPatchContentProvider(PatchDiffNode node) {
      this.node = node;
    }

    @Override
    public Object[] getChildren(Object element) {
      if (element instanceof IWorkspaceRoot) {
        IProject[] allProjects = ((IWorkspaceRoot) element).getProjects();
        ArrayList<IProject> accessibleProjects = new ArrayList<>();
        for (IProject allProject : allProjects) {
          if (allProject.isOpen()) {
            accessibleProjects.add(allProject);
          }
        }
        return accessibleProjects.toArray();
      }
      if (element instanceof IProject && node instanceof PatchProjectDiffNode) {
        return new Object[0];
      }
      return super.getChildren(element);
    }
  }

  private final PatchDiffNode fSelectedNode;
  private final WorkspacePatcher fPatcher;
  private TreeViewer fViewer;
  private IResource fSelectedResource;

  public RetargetPatchElementDialog(Shell shell, WorkspacePatcher patcher, PatchDiffNode node) {
    super(shell);
    Assert.isNotNull(patcher);
    Assert.isNotNull(node);
    setShellStyle(getShellStyle() | SWT.RESIZE);
    this.fPatcher = patcher;
    fSelectedNode = node;
  }

  @Override
  protected Control createButtonBar(Composite parent) {
    Control control = super.createButtonBar(parent);
    Button okButton = this.getButton(IDialogConstants.OK_ID);
    okButton.setEnabled(false);
    return control;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    getShell().setText(PatchMessages.PreviewPatchPage_RetargetPatch);

    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Label label = new Label(composite, SWT.LEFT | SWT.WRAP);
    label.setText(getTreeLabel());
    label.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

    fViewer = new TreeViewer(composite, SWT.BORDER);
    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    gd.widthHint = 0;
    gd.heightHint = 0;
    fViewer.getTree().setLayoutData(gd);
    fViewer.setContentProvider(new RetargetPatchContentProvider(fSelectedNode));
    fViewer.setLabelProvider(new WorkbenchLabelProvider());
    fViewer.setComparator(new ResourceComparator(ResourceComparator.NAME));
    fViewer.setInput(getViewerInput());

    IResource resource = getInitialSelection();
    if (resource != null) {
      fViewer.setSelection(new StructuredSelection(resource));
      fViewer.expandToLevel(resource, 0);
    }
    setupListeners();
    return parent;
  }

  private IResource getViewerInput() {
    if (fPatcher.isWorkspacePatch()) {
      return ResourcesPlugin.getWorkspace().getRoot();
    }
    return fPatcher.getTarget();
  }

  private IResource getInitialSelection() {
    if (fSelectedNode instanceof PatchFileDiffNode node) {
      return fPatcher.getTargetFile(node.getDiffResult().getDiff());
    } else if (fSelectedNode instanceof HunkDiffNode node) {
      return fPatcher.getTargetFile(node.getHunkResult().getDiffResult().getDiff());
    } else if (fSelectedNode instanceof PatchProjectDiffNode node) {
      DiffProject diffProject = node.getDiffProject();
      return Utilities.getProject(diffProject);
    }
    return null;
  }

  private String getTreeLabel() {
    if (fSelectedNode instanceof PatchProjectDiffNode node) {
      DiffProject project = node.getDiffProject();
      return PatchMessages.bind(PatchMessages.PreviewPatchPage_SelectProject, project.getName());
    } else if (fSelectedNode instanceof PatchFileDiffNode node) {
      FilePatch2 diff = node.getDiffResult().getDiff();
      return PatchMessages.bind(PatchMessages.RetargetPatchElementDialog_0, fPatcher.getPath(diff));
    } else if (fSelectedNode instanceof HunkDiffNode node) {
      Hunk hunk = node.getHunkResult().getHunk();
      return PatchMessages.bind(PatchMessages.RetargetPatchElementDialog_1, fPatcher.getPath(hunk.getParent()));
    }
    return "";
  }

  @Override
  protected void okPressed() {
    if (fSelectedResource != null) {
      if (fSelectedNode instanceof PatchProjectDiffNode node && fSelectedResource instanceof IProject) {
        DiffProject project = node.getDiffProject();
        fPatcher.retargetProject(project, (IProject) fSelectedResource);
      } else if (fSelectedNode instanceof PatchFileDiffNode node && fSelectedResource instanceof IFile) {
        FilePatch2 diff = node.getDiffResult().getDiff();
        fPatcher.retargetDiff(diff, (IFile) fSelectedResource);
      } else if (fSelectedNode instanceof HunkDiffNode node && fSelectedResource instanceof IFile) {
        fPatcher.retargetHunk(node.getHunkResult().getHunk(), (IFile) fSelectedResource);
      }
    }
    super.okPressed();
  }

  void setupListeners() {
    fViewer.addSelectionChangedListener(event -> {
      IStructuredSelection s = event.getStructuredSelection();
      Object obj = s.getFirstElement();
      if (obj instanceof IResource) {
        fSelectedResource = (IResource) obj;
        if (fSelectedNode instanceof PatchProjectDiffNode) {
          if (fSelectedResource instanceof IProject) {
            getButton(IDialogConstants.OK_ID).setEnabled(true);
          }
        } else if (fSelectedNode instanceof PatchFileDiffNode || fSelectedNode instanceof HunkDiffNode) {
          if (fSelectedResource instanceof IFile) {
            getButton(IDialogConstants.OK_ID).setEnabled(true);
          }
        }
      }
    });
    fViewer.addDoubleClickListener(event -> {
      ISelection s = event.getSelection();
      if (s instanceof IStructuredSelection) {
        Object item = ((IStructuredSelection) s).getFirstElement();
        if (fViewer.getExpandedState(item)) {
          fViewer.collapseToLevel(item, 1);
        } else {
          fViewer.expandToLevel(item, 1);
        }
      }
    });
  }

  @Override
  protected Point getInitialSize() {
    final Point size = super.getInitialSize();
    size.x = convertWidthInCharsToPixels(75);
    size.y += convertHeightInCharsToPixels(20);
    return size;
  }
}
