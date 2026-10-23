package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.EclipseProjectConfigurator}: detects Eclipse projects (folders with
 * a .project file) and refreshes their content when they are configured.
 */
public class EclipseProjectConfigurator implements ProjectConfigurator {
  private static final String DESCRIPTION_FILE_NAME = ".project"; //$NON-NLS-1$

  @Override
  public Set<File> findConfigurableLocations(File root, IProgressMonitor monitor) {
    return findConfigurableLocations(root, false, monitor);
  }

  @Override
  public Set<File> findConfigurableLocations(File root, boolean skipDotFolders, IProgressMonitor monitor) {
    Set<File> res = new LinkedHashSet<>();
    collect(root, skipDotFolders, res);
    return res;
  }

  /** The folders with a .project file, the folder itself first, then the sub-folders (WizardProjectsImportPage). */
  private static void collect(File dir, boolean skipDotFolders, Set<File> res) {
    if (!dir.isDirectory() || (skipDotFolders && dir.getName().startsWith(".")))
      return;
    if (new File(dir, DESCRIPTION_FILE_NAME).isFile())
      res.add(dir);
    File[] children = dir.listFiles(File::isDirectory);
    if (children == null)
      return;
    java.util.Arrays.sort(children);
    for (File child : children)
      collect(child, skipDotFolders, res);
  }

  @Override
  public boolean shouldBeAnEclipseProject(IContainer container, IProgressMonitor monitor) {
    return container.getFile(Path.fromOSString(DESCRIPTION_FILE_NAME)).exists();
  }

  @Override
  public Set<IFolder> getFoldersToIgnore(IProject project, IProgressMonitor monitor) {
    return null;
  }

  @Override
  public boolean canConfigure(IProject project, Set<IPath> ignoredPaths, IProgressMonitor monitor) {
    return true;
  }

  @Override
  public void removeDirtyDirectories(Map<File, List<ProjectConfigurator>> proposals) {
    // nothing to do: we cannot infer that a directory is dirty from .project
  }

  @Override
  public void configure(IProject project, Set<IPath> excludedDirectories, IProgressMonitor monitor) {
    try {
      project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
    } catch (Exception ex) {
      com.unitesk.requality.core.log.RLogger.logError(ex);
    }
  }
}
