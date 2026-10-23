package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;

/** {@code org.eclipse.ui.internal.wizards.datatransfer.EclipseWorkspaceConfigurator}: detects an Eclipse workspace (a folder with .metadata). */
public class EclipseWorkspaceConfigurator implements ProjectConfigurator {
  private static final String METADATA = ".metadata"; //$NON-NLS-1$

  @Override
  public Set<File> findConfigurableLocations(File root, IProgressMonitor monitor) {
    return Collections.emptySet();
  }

  @Override
  public boolean shouldBeAnEclipseProject(IContainer container, IProgressMonitor monitor) {
    return container.getFolder(Path.fromOSString(METADATA)).exists();
  }

  @Override
  public Set<IFolder> getFoldersToIgnore(IProject project, IProgressMonitor monitor) {
    Set<IFolder> res = new HashSet<>();
    res.add(project.getFolder(Path.fromOSString(METADATA)));
    return res;
  }

  @Override
  public boolean canConfigure(IProject project, Set<IPath> ignoredPaths, IProgressMonitor monitor) {
    return false;
  }

  @Override
  public void configure(IProject project, Set<IPath> excludedDirectories, IProgressMonitor monitor) {
    // Nothing to do
  }
}
