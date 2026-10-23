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

import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.patch.api.PatchConfiguration;
import com.unitesk.requality.vscode.ui.compare.patch.core.DiffProject;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffElement;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * {@code org.eclipse.compare.internal.patch.PatchProjectDiffNode}: the project of a workspace patch in the review tree.
 */
public class PatchProjectDiffNode extends PatchDiffNode {
  private final DiffProject project;
  private final PatchConfiguration configuration;

  public PatchProjectDiffNode(IDiffContainer parent, DiffProject project, PatchConfiguration configuration) {
    super(project, parent, Differencer.NO_CHANGE);
    this.project = project;
    this.configuration = configuration;
  }

  @Override
  public String getName() {
    return project.getName();
  }

  @Override
  public Image getImage() {
    Image image = CompareUI.getImage(Utilities.getProject(project));
    if (containsProblems()) {
      image = HunkTypedElement.getHunkErrorImage(image, true);
    }
    return image;
  }

  private boolean containsProblems() {
    for (IDiffElement element : getChildren()) {
      if (element instanceof PatchFileDiffNode node && node.getDiffResult().containsProblems()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getType() {
    return ITypedElement.FOLDER_TYPE;
  }

  @Override
  protected PatchConfiguration getConfiguration() {
    return configuration;
  }

  public DiffProject getDiffProject() {
    return project;
  }

  @Override
  public IResource getResource() {
    return ResourcesPlugin.getWorkspace().getRoot().getProject(getDiffProject().getName());
  }
}
