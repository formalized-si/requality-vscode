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

import java.io.InputStream;

import com.unitesk.requality.vscode.core.Icons;
import com.unitesk.requality.vscode.ui.compare.IEncodedStreamContentAccessor;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.patch.core.FileDiffResult;
import com.unitesk.requality.vscode.ui.compare.patch.core.HunkResult;
import com.unitesk.requality.vscode.ui.compare.patch.api.IHunk;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * {@code org.eclipse.compare.internal.patch.HunkTypedElement}: the before or the after state of a hunk of a patch.
 * The hunk image carries the error overlay of a hunk that does not match and the warning overlay of a fuzzy match.
 */
public class HunkTypedElement implements ITypedElement, IEncodedStreamContentAccessor, IAdaptable {
  /** {@code ICompareUIConstants.HUNK_OBJ} (the compare images are copied to {@code icons/compare}). */
  static final String HUNK_OBJ = "icons/compare/obj16/hunk_obj.png";
  /** {@code ICompareUIConstants.ERROR_OVERLAY}. */
  static final String ERROR_OVERLAY = "icons/compare/ovr16/error_ov.png";
  /** {@code ICompareUIConstants.WARNING_OVERLAY}. */
  static final String WARNING_OVERLAY = "icons/compare/ovr16/warning_ov.png";
  /** {@code ICompareUIConstants.IS_MERGED_OVERLAY}. */
  static final String IS_MERGED_OVERLAY = "icons/compare/ovr16/merged_ov.png";
  /** {@code ICompareUIConstants.REMOVED_OVERLAY}. */
  static final String REMOVED_OVERLAY = "icons/compare/ovr16/removed_ov.png";
  /** {@code ICompareUIConstants.COMPARE_IMAGE_WIDTH}. */
  static final int COMPARE_IMAGE_WIDTH = 22;

  private final HunkResult fHunkResult;
  private final boolean fIsAfterState;
  private final boolean fFullContext;

  public HunkTypedElement(HunkResult result, boolean isAfterState, boolean fullContext) {
    this.fHunkResult = result;
    this.fIsAfterState = isAfterState;
    this.fFullContext = fullContext;
  }

  @Override
  public Image getImage() {
    Image image = new Image(null, Icons.resource(HUNK_OBJ).getAbsolutePath());
    if (!fHunkResult.isOK()) {
      return getHunkErrorImage(image, true);
    } else if (fHunkResult.getFuzz() > 0) {
      return getHunkOverlayImage(image, WARNING_OVERLAY, true);
    }
    return image;
  }

  /** {@code HunkTypedElement.getHunkErrorImage}: the base image with the error overlay beside it. */
  public static Image getHunkErrorImage(Image baseImage, boolean onLeft) {
    return getHunkOverlayImage(baseImage, ERROR_OVERLAY, onLeft);
  }

  /** {@code DiffImageDescriptor}: the base image with the overlay drawn beside it (see {@link Icons#composeBeside}). */
  static Image getHunkOverlayImage(Image baseImage, String overlay, boolean onLeft) {
    String path = Icons.composeBeside(baseImage == null ? null : baseImage.getPath(),
        Icons.resource(overlay).getAbsolutePath(), COMPARE_IMAGE_WIDTH, onLeft);
    return path == null ? baseImage : new Image(null, path);
  }

  public boolean isManuallyMerged() {
    return getPatcher().isManuallyMerged(getHunkResult().getHunk());
  }

  private Patcher getPatcher() {
    return Patcher.getPatcher(fHunkResult.getDiffResult().getConfiguration());
  }

  @Override
  public String getName() {
    return fHunkResult.getHunk().getLabel();
  }

  @Override
  public String getType() {
    return fHunkResult.getDiffResult().getDiff().getTargetPath(fHunkResult.getDiffResult().getConfiguration())
        .getFileExtension();
  }

  @Override
  public InputStream getContents() throws CoreException {
    String contents = fHunkResult.getContents(fIsAfterState, fFullContext);
    return FileDiffResult.asInputStream(contents, fHunkResult.getCharset());
  }

  @Override
  public String getCharset() throws CoreException {
    return fHunkResult.getCharset();
  }

  public HunkResult getHunkResult() {
    return fHunkResult;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getAdapter(Class<T> adapter) {
    if (adapter == IHunk.class) {
      return (T) fHunkResult.getHunk();
    }
    if (adapter == HunkResult.class) {
      return (T) fHunkResult;
    }
    return null;
  }
}
