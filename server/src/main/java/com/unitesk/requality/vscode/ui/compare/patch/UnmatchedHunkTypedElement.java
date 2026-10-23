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
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.IContentChangeListener;
import com.unitesk.requality.vscode.ui.compare.IContentChangeNotifier;
import com.unitesk.requality.vscode.ui.compare.IEditableContent;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.compare.patch.core.HunkResult;
import com.unitesk.requality.vscode.ui.compare.patch.api.PatchConfiguration;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/**
 * {@code org.eclipse.compare.internal.patch.UnmatchedHunkTypedElement}: the local copy of a hunk that does not match. Its
 * contents can be merged by hand; the merged contents are kept for the patch until it is applied.
 */
public class UnmatchedHunkTypedElement extends HunkTypedElement implements IContentChangeNotifier, IEditableContent {
  private final List<IContentChangeListener> listeners = new ArrayList<>();

  public UnmatchedHunkTypedElement(HunkResult result) {
    super(result, false, true);
  }

  @Override
  public synchronized void addContentChangeListener(IContentChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public synchronized void removeContentChangeListener(IContentChangeListener listener) {
    listeners.remove(listener);
  }

  private void fireContentChanged() {
    List<IContentChangeListener> copy;
    synchronized (this) {
      copy = new ArrayList<>(listeners);
    }
    for (IContentChangeListener listener : copy) {
      listener.contentChanged(this);
    }
  }

  @Override
  public boolean isEditable() {
    IFile file = ((WorkspaceFileDiffResult) getHunkResult().getDiffResult()).getTargetFile();
    return file != null && file.isAccessible();
  }

  @Override
  public ITypedElement replace(ITypedElement dest, ITypedElement src) {
    return null;
  }

  @Override
  public void setContent(byte[] newContent) {
    getPatcher().setManuallyMerged(getHunkResult().getHunk(), true);
    getPatcher().cacheContents(getDiff(), newContent);
    fireContentChanged();
  }

  private FilePatch2 getDiff() {
    return getHunkResult().getDiffResult().getDiff();
  }

  private Patcher getPatcher() {
    return Patcher.getPatcher(getConfiguration());
  }

  @Override
  public InputStream getContents() throws CoreException {
    if (getPatcher().hasCachedContents(getDiff())) {
      return new ByteArrayInputStream(getPatcher().getCachedContents(getDiff()));
    }
    List<String> lines = getHunkResult().getDiffResult().getAfterLines();
    String content = LineReader.createString(getHunkResult().getDiffResult().isPreserveLineDelimiters(), lines);
    byte[] bytes = null;
    if (getCharset() != null) {
      try {
        bytes = content.getBytes(getCharset());
      } catch (UnsupportedEncodingException e) {
        RLogger.logError(e);
      }
    }
    if (bytes == null) {
      bytes = content.getBytes();
    }
    return new ByteArrayInputStream(bytes);
  }

  private PatchConfiguration getConfiguration() {
    return getHunkResult().getDiffResult().getConfiguration();
  }
}
