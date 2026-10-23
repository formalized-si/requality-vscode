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

import java.text.MessageFormat;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.navigator.ResourceComparator;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchContentProvider;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.compare.internal.patch.PatchTargetPage}: the target of the patch, when the patch is not a workspace
 * patch: the workspace root, or the selected project, folder or file. This page only shows up if the user is trying to
 * apply a non-workspace rooted patch.
 */
public class PatchTargetPage extends WizardPage {
  private boolean fShowError = false;
  private TreeViewer fPatchTargets;
  private Button useWorkspaceAsTarget;
  private Button selectTarget;
  protected WorkspacePatcher fPatcher;

  protected static final String PATCHTARGETPAGE_NAME = "PatchTargetPage";

  public PatchTargetPage(WorkspacePatcher patcher) {
    super(PATCHTARGETPAGE_NAME, PatchMessages.PatchTargetPage_title, null);
    setMessage(PatchMessages.PatchTargetPage_message);
    fPatcher = patcher;
  }

  /** The path of the text field, made absolute. */
  protected IPath getPathFromText(Text textField) {
    return (Path.fromOSString(textField.getText())).makeAbsolute();
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
    setControl(composite);

    useWorkspaceAsTarget = createRadioButton(composite, PatchMessages.PatchTargetPage_0, 1);
    selectTarget = createRadioButton(composite, PatchMessages.InputPatchPage_SelectInput, 1);
    buildInputGroup(composite);
    updateWidgetEnablements();

    useWorkspaceAsTarget.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        fShowError = true;
        if (useWorkspaceAsTarget.getSelection()) {
          fPatchTargets.getTree().setEnabled(false);
          fPatcher.setTarget(ResourcesPlugin.getWorkspace().getRoot());
        } else {
          fPatchTargets.getTree().setEnabled(true);
          fPatcher.setTarget(Utilities.getFirstResource(fPatchTargets.getSelection()));
        }
        markPreviewPageToRecalucateIfNonWorkspacePatch();
        updateWidgetEnablements();
      }
    });
  }

  private void markPreviewPageToRecalucateIfNonWorkspacePatch() {
    if (!fPatcher.isWorkspacePatch()) {
      PreviewPatchPage2 previewPage = (PreviewPatchPage2) getWizard().getPage(PreviewPatchPage2.PREVIEWPATCHPAGE_NAME);
      previewPage.pageRecalculate = true;
    }
  }

  private Button createRadioButton(Composite parent, String label, int span) {
    Button button = new Button(parent, SWT.RADIO);
    button.setText(label);
    GridData data = new GridData();
    data.horizontalSpan = span;
    button.setLayoutData(data);
    return button;
  }

  @Override
  public IWizardPage getNextPage() {
    if (!fPatcher.isWorkspacePatch() && fPatcher.getTarget() instanceof IFile && fPatcher.getDiffs().length > 1) {
      InputPatchPage inputPage = (InputPatchPage) getWizard().getPage(InputPatchPage.INPUTPATCHPAGE_NAME);
      String source;
      switch (inputPage.getInputMethod()) {
        case InputPatchPage.CLIPBOARD:
          source = PatchMessages.InputPatchPage_Clipboard_title;
          break;
        case InputPatchPage.FILE:
          source = PatchMessages.InputPatchPage_PatchFile_title;
          break;
        case InputPatchPage.WORKSPACE:
          source = PatchMessages.InputPatchPage_WorkspacePatch_title;
          break;
        default: // URL
          source = "";
          break;
      }
      String format = PatchMessages.InputPatchPage_SingleFileError_format;
      String message = MessageFormat.format(format, source);
      MessageDialog.openInformation(null, PatchMessages.InputPatchPage_PatchErrorDialog_title, message);
      return this;
    }
    return super.getNextPage();
  }

  @Override
  public boolean canFlipToNextPage() {
    return isPageComplete();
  }

  private void buildInputGroup(Composite parent) {
    Tree tree = new Tree(parent, SWT.BORDER);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 200;
    tree.setLayoutData(gd);
    fPatchTargets = new TreeViewer(tree);
    fPatchTargets.setLabelProvider(new WorkbenchLabelProvider());
    fPatchTargets.setContentProvider(new WorkbenchContentProvider());
    fPatchTargets.setComparator(new ResourceComparator(ResourceComparator.NAME));
    fPatchTargets.setInput(ResourcesPlugin.getWorkspace().getRoot());
    IResource target = fPatcher.getTarget();
    if (target != null && !(target instanceof IWorkspaceRoot)) {
      fPatchTargets.expandToLevel(target, 0);
      fPatchTargets.setSelection(new StructuredSelection(target));
    }
    fPatchTargets.addSelectionChangedListener(event -> {
      fShowError = true;
      fPatcher.setTarget(Utilities.getFirstResource(event.getSelection()));
      updateWidgetEnablements();
    });
    fPatchTargets.addDoubleClickListener(event -> ((PatchWizard) getWizard()).showPage(getNextPage()));
  }

  /** Updates the enable state of this page's controls. */
  private void updateWidgetEnablements() {
    String error = null;
    if (fPatcher.getTarget() == null) {
      useWorkspaceAsTarget.setSelection(false);
      selectTarget.setSelection(true);
      error = PatchMessages.InputPatchPage_NothingSelected_message;
      setPageComplete(false);
      if (fShowError) {
        setErrorMessage(error);
      }
      return;
    }
    setErrorMessage(null);
    useWorkspaceAsTarget.setSelection(fPatcher.getTarget() instanceof IWorkspaceRoot);
    selectTarget.setSelection(!useWorkspaceAsTarget.getSelection());
    setPageComplete(true);
  }

  /** Called when the Finish button is pressed (the target is applied by the wizard). */
  public boolean finish() {
    return true;
  }
}
