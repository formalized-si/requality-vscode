/*******************************************************************************
 * Copyright (c) 2000, 2021 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.compare.patch;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * {@code org.eclipse.compare.internal.patch.PatchWizard}: the Apply Patch wizard. The input page reads the patch (when none
 * is given), the target page chooses the resource the patch applies to, the review page shows the file patches and the
 * options; Finish applies the patch to the workspace files (their previous contents are kept in the local history).
 */
public class PatchWizard extends Wizard {
  protected InputPatchPage fPatchWizardPage;
  protected PatchTargetPage fPatchTargetPage;
  protected PreviewPatchPage2 fPreviewPage2;
  private final WorkspacePatcher fPatcher;
  private final CompareConfiguration fConfiguration;
  private IStorage patch;
  private boolean patchReadIn = false;
  /** {@code PatchWizard.DIALOG_SETTINGS_KEY}: the section of the wizard in the dialog settings. */
  private static final String DIALOG_SETTINGS_KEY = "PatchWizard";
  private boolean fHasNewDialogSettings;

  public PatchWizard(IStorage patch, IResource target, CompareConfiguration configuration) {
    Assert.isNotNull(configuration);
    this.fConfiguration = configuration;
    setWindowTitle(PatchMessages.PatchWizard_title);
    initializeDialogSettings();
    // VSCODE: a patch file of the workspace without a target applies to the folder that contains it (the target page
    // shows that folder as selected, the user may choose another one)
    if (target == null && patch instanceof IResource patchResource) {
      target = patchResource.getParent();
    }
    fPatcher = new WorkspacePatcher(target);
    if (patch != null) {
      try {
        fPatcher.parse(patch);
        this.patch = patch;
        patchReadIn = true;
      } catch (IOException e) {
        RLogger.logError(e);
        MessageDialog.openError(Display.getDefault().getActiveShell(), PatchMessages.InputPatchPage_PatchErrorDialog_title,
            PatchMessages.InputPatchPage_ParseError_message);
      } catch (CoreException e) {
        RLogger.logError(e);
        MessageDialog.openError(Display.getDefault().getActiveShell(), PatchMessages.InputPatchPage_PatchErrorDialog_title,
            PatchMessages.InputPatchPage_PatchFileNotFound_message);
      }
    }
  }

  protected WorkspacePatcher getPatcher() {
    return fPatcher;
  }

  protected IStorage getPatch() {
    return patch;
  }

  IResource getTarget() {
    return fPatcher.getTarget();
  }

  @Override
  public void addPages() {
    if (patch == null) {
      addPage(fPatchWizardPage = new InputPatchPage(this));
    }
    if (patch == null || (!fPatcher.isWorkspacePatch() && !fPatcher.isGitPatch())) {
      addPage(fPatchTargetPage = new PatchTargetPage(fPatcher));
    } else if (fPatcher.isGitPatch()) {
      fPatcher.setTarget(ResourcesPlugin.getWorkspace().getRoot());
    }
    fPreviewPage2 = new PreviewPatchPage2(fPatcher, fConfiguration);
    addPage(fPreviewPage2);
  }

  /** {@code PatchWizard.initializeDialogSettings}: the section of the wizard in the workbench dialog settings. */
  private void initializeDialogSettings() {
    IDialogSettings workbenchSettings = Activator.getDefault().getDialogSettings();
    IDialogSettings section = workbenchSettings.getSection(DIALOG_SETTINGS_KEY);
    if (section == null) {
      fHasNewDialogSettings = true;
    } else {
      fHasNewDialogSettings = false;
      setDialogSettings(section);
    }
  }

  @Override
  public boolean performFinish() {
    IWizardPage currentPage = getContainer().getCurrentPage();
    if (currentPage.getName().equals(PreviewPatchPage2.PREVIEWPATCHPAGE_NAME)) {
      PreviewPatchPage2 previewPage = (PreviewPatchPage2) currentPage;
      previewPage.ensureContentsSaved();
    }
    if (fPatchWizardPage != null) {
      // make sure that the patch has been read
      if (!fPatchWizardPage.isPatchRead()) {
        fPatchWizardPage.readInPatch();
      }
      fPatcher.refresh();
      // make sure that the patch is not invalid
      if (!fPatchWizardPage.checkPageComplete()) {
        return false;
      }
    } else {
      // either we have a patch from the patch input page or one has been specified; double check this
      Assert.isNotNull(patch);
      Assert.isTrue(patchReadIn);
    }
    if (!currentPage.getName().equals(PreviewPatchPage2.PREVIEWPATCHPAGE_NAME) && fPatcher.hasRejects()) {
      if (!MessageDialog.openConfirm(getShell(), PatchMessages.PatchWizard_0, PatchMessages.PatchWizard_1)) {
        return false;
      }
    }
    try {
      finishOperation();
    } catch (InvocationTargetException e) {
      RLogger.logError(e);
      MessageDialog.openError(getShell(), PatchMessages.PatchWizard_title,
          PatchMessages.PatchWizard_unexpectedException_message);
    } catch (InterruptedException e) {
      // cannot happen
    }
    // Save the dialog settings
    if (fHasNewDialogSettings) {
      IDialogSettings workbenchSettings = Activator.getDefault().getDialogSettings();
      IDialogSettings section = workbenchSettings.addNewSection(DIALOG_SETTINGS_KEY);
      setDialogSettings(section);
    }
    // the input and the options of the wizard are remembered for the next wizard
    if (fPatchWizardPage != null) {
      fPatchWizardPage.saveWidgetValues();
    }
    fPreviewPage2.saveWidgetValues();
    return true;
  }

  /**
   * The operation of Finish, run with the progress of the wizard container: the patch is applied to the workspace.
   * VSCODE: the Synchronize with Patch wizard overrides it to create its synchronize participant instead of applying the
   * patch ({@code ApplyPatchSynchronizationWizard}).
   */
  protected void finishOperation() throws InvocationTargetException, InterruptedException {
    // VSCODE: no scheduling rule is taken for the workspace modification (the emulated workspace has no rules).
    getContainer().run(true, false, new IRunnableWithProgress() {
      @Override
      public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
        try {
          fPatcher.applyAll(monitor, resources -> com.unitesk.requality.vscode.ui.compare.Utilities
              .validateResources(resources, getShell(), PatchMessages.PatchWizard_title));
        } catch (CoreException e) {
          throw new InvocationTargetException(e);
        }
      }
    });
  }

  public void showPage(IWizardPage page) {
    getContainer().showPage(page);
  }

  @Override
  public IWizardPage getNextPage(IWizardPage page) {
    // no patch has been read in yet, input patch page
    if (!patchReadIn) {
      return fPatchWizardPage;
    }
    // Check to see if we're already on the patch target page and if a target has been set - if it has return the next
    // page in sequence (the preview patch page)
    if (page instanceof PatchTargetPage && getTarget() != null) {
      return super.getNextPage(page);
    } else if (page instanceof InputPatchPage && !fPatcher.isWorkspacePatch()) {
      // Check to see if we need a target
      return fPatchTargetPage;
    }
    return super.getNextPage(page);
  }

  /** Used to report that the patch has been read in. */
  protected void patchReadIn() {
    patchReadIn = true;
  }

  public CompareConfiguration getCompareConfiguration() {
    return fConfiguration;
  }

  @Override
  public boolean canFinish() {
    IWizardPage currentPage = getContainer().getCurrentPage();
    if (currentPage.getName().equals(PreviewPatchPage2.PREVIEWPATCHPAGE_NAME)) {
      return currentPage.isPageComplete();
    }
    return super.canFinish();
  }
}
