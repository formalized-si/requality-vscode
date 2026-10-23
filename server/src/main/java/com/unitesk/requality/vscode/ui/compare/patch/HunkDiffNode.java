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

import com.unitesk.requality.vscode.ui.compare.patch.core.HunkResult;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.patch.api.PatchConfiguration;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.resources.IResource;

/**
 * {@code org.eclipse.compare.internal.patch.HunkDiffNode}: a hunk of a file of the patch in the review tree; the left side
 * is the local copy, the right side the hunk (or the unmatched hunk, for a hunk that does not match).
 */
public class HunkDiffNode extends PatchDiffNode {
  private final HunkResult result;

  public static HunkDiffNode createDiffNode(PatchFileDiffNode parent, HunkResult result, boolean fullContext) {
    return createDiffNode(parent, result, fullContext, fullContext, fullContext);
  }

  public static HunkDiffNode createDiffNode(PatchFileDiffNode parent, HunkResult result, boolean ancestorFullContext,
      boolean leftFullContext, boolean rightFullContext) {
    return new HunkDiffNode(result, parent, Differencer.CHANGE, getAncestorElement(result, ancestorFullContext),
        getLeftElement(result, leftFullContext), getRightElement(result, rightFullContext));
  }

  public static ITypedElement getRightElement(HunkResult result, boolean fullContext) {
    return new HunkTypedElement(result, true /* isResult */, fullContext);
  }

  private static ITypedElement getLeftElement(HunkResult result, boolean fullContext) {
    if (fullContext && !result.isOK()) {
      return new UnmatchedHunkTypedElement(result);
    }
    return new HunkTypedElement(result, false /* before state */, fullContext);
  }

  public static ITypedElement getAncestorElement(HunkResult result, boolean fullContext) {
    if (!fullContext && result.isOK()) {
      return new HunkTypedElement(result, false /* before state */, fullContext);
    }
    if (!fullContext) {
      return null;
    }
    return new HunkTypedElement(result, false /* before state */, result.isOK());
  }

  public HunkDiffNode(HunkResult result, PatchFileDiffNode parent, int kind, ITypedElement ancestor,
      ITypedElement left, ITypedElement right) {
    super(result.getHunk(), parent, kind, ancestor, left, right);
    this.result = result;
  }

  public HunkResult getHunkResult() {
    return result;
  }

  @Override
  protected PatchConfiguration getConfiguration() {
    return result.getDiffResult().getConfiguration();
  }

  public boolean isManuallyMerged() {
    Object left = getLeft();
    if (left instanceof UnmatchedHunkTypedElement element) {
      return element.isManuallyMerged();
    }
    return false;
  }

  public boolean isFuzzUsed() {
    return result.getFuzz() > 0;
  }

  public boolean isAllContextIgnored() {
    int fuzz = result.getFuzz();
    if (fuzz > 0) {
      String[] lines = result.getHunk().getLines();
      int contextLines = 0;
      for (String line : lines) {
        char c = line.charAt(0);
        if (c == ' ') {
          contextLines++;
        } else {
          if (contextLines > 0 && fuzz >= contextLines) {
            return true;
          }
          contextLines = 0;
        }
      }
      if (contextLines > 0 && fuzz >= contextLines) {
        return true;
      }
    }
    return false;
  }

  @Override
  public IResource getResource() {
    return ((PatchFileDiffNode) getParent()).getResource();
  }
}
