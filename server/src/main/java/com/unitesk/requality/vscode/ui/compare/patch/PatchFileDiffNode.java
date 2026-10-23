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

import com.unitesk.requality.vscode.ui.compare.patch.core.FileDiffResult;
import com.unitesk.requality.vscode.ui.compare.IContentChangeListener;
import com.unitesk.requality.vscode.ui.compare.IContentChangeNotifier;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.patch.api.PatchConfiguration;
import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffContainer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffElement;
import com.unitesk.requality.vscode.ui.resources.IResource;

/**
 * {@code org.eclipse.compare.internal.patch.PatchFileDiffNode}: a file of the patch in the review tree. Its hunks are its
 * children; it changes when the contents of an unmatched hunk are merged by hand.
 */
public class PatchFileDiffNode extends PatchDiffNode implements IContentChangeListener {
  private final FileDiffResult result;

  public static PatchFileDiffNode createDiffNode(IDiffContainer parent, FileDiffResult result) {
    return new PatchFileDiffNode(result, parent, getKind(result), getAncestorElement(result),
        getLeftElement(result), getRightElement(result));
  }

  public static int getKind(FileDiffResult result) {
    if (!result.hasMatches()) {
      return Differencer.NO_CHANGE;
    }
    int fileDiffKind = result.getDiff().getDiffType(result.getConfiguration().isReversed());
    int kind = convertFileDiffTypeToDifferencerType(fileDiffKind);
    return kind | Differencer.RIGHT;
  }

  private static int convertFileDiffTypeToDifferencerType(int fileDiffKind) {
    switch (fileDiffKind) {
      case FilePatch2.ADDITION:
        return Differencer.ADDITION;
      case FilePatch2.DELETION:
        return Differencer.DELETION;
      case FilePatch2.CHANGE:
        return Differencer.CHANGE;
      default:
        return Differencer.CHANGE;
    }
  }

  public static ITypedElement getRightElement(FileDiffResult result) {
    return new PatchFileTypedElement(result, true);
  }

  private static ITypedElement getLeftElement(FileDiffResult result) {
    return new PatchFileTypedElement(result, false);
  }

  public static ITypedElement getAncestorElement(FileDiffResult result) {
    return new PatchFileTypedElement(result, false);
  }

  public PatchFileDiffNode(FileDiffResult result, IDiffContainer parent, int kind, ITypedElement ancestor,
      ITypedElement left, ITypedElement right) {
    super(result.getDiff(), parent, kind, ancestor, left, right);
    this.result = result;
  }

  public FileDiffResult getDiffResult() {
    return result;
  }

  @Override
  protected PatchConfiguration getConfiguration() {
    return result.getConfiguration();
  }

  @Override
  public void add(IDiffElement diff) {
    super.add(diff);
    if (diff instanceof HunkDiffNode node) {
      Object left = node.getLeft();
      if (left instanceof IContentChangeNotifier notifier) {
        notifier.addContentChangeListener(this);
      }
    }
  }

  @Override
  public void contentChanged(IContentChangeNotifier source) {
    fireChange();
  }

  @Override
  public int getKind() {
    int kind = super.getKind();
    if (kind == Differencer.NO_CHANGE && getPatcher().hasCachedContents(getDiffResult().getDiff())) {
      return Differencer.CHANGE | Differencer.RIGHT;
    }
    return kind;
  }

  public boolean fileExists() {
    IResource file = getResource();
    return file != null && file.isAccessible();
  }

  @Override
  public IResource getResource() {
    return ((WorkspaceFileDiffResult) getDiffResult()).getTargetFile();
  }
}
