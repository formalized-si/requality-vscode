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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.IEncodedStreamContentAccessor;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.patch.core.DiffProject;
import com.unitesk.requality.vscode.ui.compare.patch.core.FileDiffResult;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * {@code org.eclipse.compare.internal.patch.PatchFileTypedElement}: the before or the after state of a file of the patch
 * (the contents of the file with the patch applied, or without it).
 */
public class PatchFileTypedElement implements ITypedElement, IEncodedStreamContentAccessor {
  private final FileDiffResult result;
  private final boolean isAfterState;

  public PatchFileTypedElement(FileDiffResult result, boolean isAfterState) {
    this.result = result;
    this.isAfterState = isAfterState;
  }

  @Override
  public Image getImage() {
    IFile file = getPatcher().getTargetFile(result.getDiff());
    if (file == null) {
      DiffProject project = result.getDiff().getProject();
      if (project != null) {
        file = Utilities.getProject(project).getFile(result.getDiff().getPath(result.getConfiguration().isReversed()));
      } else {
        IResource target = getPatcher().getTarget();
        if (target instanceof IFile f) {
          file = f;
        } else if (target instanceof IContainer container) {
          file = container.getFile(result.getTargetPath());
        }
      }
    }
    Image image = null;
    if (file != null) {
      image = CompareUI.getImage(file);
    }
    if (result.containsProblems()) {
      image = HunkTypedElement.getHunkErrorImage(image, true);
    }
    return image;
  }

  @Override
  public String getName() {
    return result.getTargetPath().toString();
  }

  @Override
  public String getType() {
    return result.getTargetPath().getFileExtension();
  }

  @Override
  public String getCharset() throws CoreException {
    return result.getCharset();
  }

  @Override
  public InputStream getContents() throws CoreException {
    if (isAfterState && getPatcher().hasCachedContents(result.getDiff())) {
      return new ByteArrayInputStream(getPatcher().getCachedContents(result.getDiff()));
    }
    List<String> lines;
    if (isAfterState) {
      lines = result.getAfterLines();
    } else {
      lines = result.getBeforeLines();
    }
    String contents = LineReader.createString(getPatcher().isPreserveLineDelimiters(), lines);
    String charSet = getCharset();
    byte[] bytes = null;
    if (charSet != null) {
      try {
        bytes = contents.getBytes(charSet);
      } catch (UnsupportedEncodingException e) {
        RLogger.logError(e);
      }
    }
    if (bytes == null) {
      bytes = contents.getBytes();
    }
    return new ByteArrayInputStream(bytes);
  }

  private Patcher getPatcher() {
    return Patcher.getPatcher(result.getConfiguration());
  }
}
