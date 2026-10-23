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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.patch.api.ReaderCreator;
import com.unitesk.requality.vscode.ui.compare.patch.core.DiffProject;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.compare.internal.patch.Utilities}: the helpers of the patch wizard (the readers of the patch and
 * target storages, the selected resources, the projects of the patch).
 */
public class Utilities {

  /** The charset of a storage: the file's charset (UTF-8 for the workspace files of the VS Code emulation). */
  public static String getCharset(Object resource) {
    if (resource instanceof IFile file) {
      try {
        return file.getCharset();
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return "UTF-8";
  }

  /** The project of a diff project (the workspace project of its name). */
  public static IProject getProject(DiffProject diffProject) {
    return ResourcesPlugin.getWorkspace().getRoot().getProject(diffProject.getName());
  }

  public static ReaderCreator getReaderCreator(final IStorage storage) {
    return new ReaderCreator() {
      @Override
      public Reader createReader() throws CoreException {
        return Utilities.createReader(storage);
      }

      @Override
      public boolean canCreateReader() {
        if (storage == null || (storage instanceof IFile && !((IFile) storage).isAccessible())) {
          return false;
        }
        return true;
      }
    };
  }

  public static BufferedReader createReader(IStorage storage) throws CoreException {
    if (storage == null || (storage instanceof IFile && !((IFile) storage).isAccessible())) {
      throw new CoreException(new Status(IStatus.WARNING, "org.eclipse.compare",
          Messages.ReaderCreator_fileIsNotAccessible));
    }
    String charset = null;
    if (storage instanceof IFile file) {
      charset = file.getCharset();
    }
    InputStreamReader in = null;
    if (charset != null) {
      InputStream contents = storage.getContents();
      try {
        in = new InputStreamReader(contents, charset);
      } catch (java.io.UnsupportedEncodingException e) {
        RLogger.logError(e);
        try {
          contents.close();
        } catch (IOException e1) {
          // ignored
        }
      }
    }
    if (in == null) {
      in = new InputStreamReader(storage.getContents());
    }
    return new BufferedReader(in);
  }

  /** The first resource of the selection (the resource, or the resource an element adapts to), or null. */
  public static IResource getFirstResource(ISelection selection) {
    IResource[] resources = getResources(selection);
    return resources.length > 0 ? resources[0] : null;
  }

  /** The resources of the selected elements. */
  public static IResource[] getResources(ISelection selection) {
    List<IResource> result = new ArrayList<>();
    if (selection instanceof IStructuredSelection ss) {
      for (Object o : ss) {
        IResource r = Adapters.adapt(o, IResource.class);
        if (r != null) {
          result.add(r);
        }
      }
    }
    return result.toArray(new IResource[result.size()]);
  }

  /** Whether the input of a compare is a hunk of a patch (its element adapts to the hunk). */
  public static boolean isHunk(Object input) {
    if (input instanceof ICompareInput ci) {
      return ci.getLeft() instanceof HunkTypedElement || ci.getRight() instanceof HunkTypedElement;
    }
    return false;
  }

  /**
   * {@code Utilities.getURLContents}: the contents of the URL, read in the calling thread. VSCODE: the progress dialog
   * of Eclipse (opening the connection and fetching the content) is not shown.
   */
  public static String getURLContents(URL url) throws IOException {
    URLConnection connection = url.openConnection();
    try (InputStream in = connection.getInputStream()) {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  /**
   * {@code org.eclipse.jface.text.TextUtilities.determineLineDelimiter}: the first line delimiter of the text, or the
   * default one when the text has none.
   */
  public static String determineLineDelimiter(String text, String defaultDelimiter) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\r')
        return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? "\r\n" : "\r";
      if (c == '\n')
        return "\n";
    }
    return defaultDelimiter;
  }
}
