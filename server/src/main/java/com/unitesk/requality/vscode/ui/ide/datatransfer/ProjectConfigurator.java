package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.ui.wizards.datatransfer.ProjectConfigurator}: detects and configures the projects of an import
 * (natures and other settings). Project configurators are contributed via the {@code org.eclipse.ui.ide.projectConfigurators}
 * extension point and must be stateless.
 */
public interface ProjectConfigurator {
  /**
   * Detects the directories (at any depth below the root) that can be imported as projects configured by this
   * configurator.
   */
  Set<File> findConfigurableLocations(File root, IProgressMonitor monitor);

  /**
   * Same as {@link #findConfigurableLocations(File, IProgressMonitor)}, but folders starting with a dot (such as
   * {@code .git}) are not visited when skipDotFolders is set.
   */
  default Set<File> findConfigurableLocations(File root, boolean skipDotFolders, IProgressMonitor monitor) {
    return findConfigurableLocations(root, monitor);
  }

  /** Removes from the proposals the directories that should not be proposed, such as build output directories. */
  default void removeDirtyDirectories(Map<File, List<ProjectConfigurator>> proposals) {
  }

  /** True when the container should also be imported as a project by this configurator. */
  boolean shouldBeAnEclipseProject(IContainer container, IProgressMonitor monitor);

  /** The folders to exclude from the analysis of the project (output directories such as bin/ or target/). */
  Set<IFolder> getFoldersToIgnore(IProject project, IProgressMonitor monitor);

  /** True when this configurator can contribute to the configuration of the project. */
  boolean canConfigure(IProject project, Set<IPath> ignoredPaths, IProgressMonitor monitor);

  /** Configures the project; called only when {@link #canConfigure} returned true. */
  void configure(IProject project, Set<IPath> ignoredPaths, IProgressMonitor monitor);
}
