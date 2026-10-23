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

import java.io.BufferedReader;
import java.io.IOException;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.patch.api.IFilePatch2;
import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.compare.patch.core.PatchReader;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;

/**
 * {@code org.eclipse.compare.patch.ApplyPatchOperation}: opens the Apply Patch wizard, with the patch when it is given (a
 * patch file of the workspace), else with the input page; the resource is the target of the patch.
 */
public class ApplyPatchOperation implements Runnable {
  private final IWorkbenchPart part;
  private final CompareConfiguration configuration;
  private final IStorage patch;
  private final IResource target;

  /** Whether the storage contains a patch (at least one file patch). */
  public static boolean isPatch(IStorage storage) throws CoreException {
    return internalParsePatch(storage).length > 0;
  }

  /** The file patches of the storage. */
  public static IFilePatch2[] parsePatch(IStorage storage) throws CoreException {
    return internalParsePatch(storage);
  }

  public ApplyPatchOperation(IWorkbenchPart part, IStorage patch, IResource target, CompareConfiguration configuration) {
    Assert.isNotNull(configuration);
    this.part = part;
    this.patch = patch;
    this.target = target;
    this.configuration = configuration;
  }

  public ApplyPatchOperation(IWorkbenchPart targetPart, IResource resource) {
    this(targetPart, null, resource, new CompareConfiguration());
  }

  /** The wizard in a dialog of the shell of the part (the active shell without a part). */
  public void openWizard() {
    PatchWizard wizard = new PatchWizard(patch, target, configuration);
    wizard.setNeedsProgressMonitor(true);
    new PatchWizardDialog(getShell(), wizard).open();
  }

  protected com.unitesk.requality.vscode.ui.swt.widgets.Shell getShell() {
    if (part == null) {
      return com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().getActiveShell();
    }
    return part.getSite().getShell();
  }

  @Override
  public void run() {
    openWizard();
  }

  private static IFilePatch2[] internalParsePatch(IStorage storage) throws CoreException {
    BufferedReader reader = Utilities.createReader(storage);
    try {
      PatchReader patchReader = new PatchReader() {
        @Override
        protected FilePatch2 createFileDiff(IPath oldPath, long oldDate, IPath newPath, long newDate) {
          return new FilePatch(oldPath, oldDate, newPath, newDate);
        }
      };
      patchReader.parse(reader);
      FilePatch2[] fileDiffs = patchReader.getAdjustedDiffs();
      IFilePatch2[] filePatch = new IFilePatch2[fileDiffs.length];
      for (int i = 0; i < fileDiffs.length; i++) {
        filePatch[i] = fileDiffs[i];
      }
      return filePatch;
    } catch (IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.compare", 0, e.getMessage(), e));
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        // ignored
      }
    }
  }
}
