package com.unitesk.requality.vscode.ui.workbench.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.ide.undo.AbstractWorkspaceOperation;
import com.unitesk.requality.vscode.ui.ide.undo.CopyResourcesOperation;
import com.unitesk.requality.vscode.ui.ide.undo.MoveResourcesOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.CopyFilesAndFoldersOperation} (and {@code MoveFilesAndFoldersOperation} with
 * {@link #move}): copies resources, or files of the file system, into a container. A resource copied into its own
 * folder gets a new name ("Name Conflict": "Copy of x"); one that exists at the destination is overwritten when the
 * user agrees, a folder by merging the copied members into it (copyExisting). A folder is never copied into itself or
 * one of its subfolders, and a destination that contains the source is not overwritten.
 */
public class CopyFilesAndFoldersOperation {
  private final Shell shell;
  private final boolean move;

  public CopyFilesAndFoldersOperation(Shell shell) {
    this(shell, false);
  }

  protected CopyFilesAndFoldersOperation(Shell shell, boolean move) {
    this.shell = shell;
    this.move = move;
  }

  /** {@code MoveFilesAndFoldersOperation}. */
  public static CopyFilesAndFoldersOperation move(Shell shell) {
    return new CopyFilesAndFoldersOperation(shell, true);
  }

  /** A source of the operation: a file or folder, a workspace resource when it is one. */
  private static final class Source {
    final File file;
    final IResource resource;

    Source(File file, IResource resource) {
      this.file = file;
      this.resource = resource;
    }

    String name() {
      return file.getName();
    }
  }

  /** One step: the source goes to the target name in the destination (merged into an existing target). */
  private static final class Step {
    final Source source;
    final String targetName;
    final boolean merge;

    Step(Source source, String targetName, boolean merge) {
      this.source = source;
      this.targetName = targetName;
      this.merge = merge;
    }
  }

  private static File canonical(File f) {
    try {
      return f.getCanonicalFile();
    } catch (IOException e) {
      return f.getAbsoluteFile();
    }
  }

  /** True when {@code inner} is {@code outer} or inside it (real locations). */
  private static boolean isWithin(File inner, File outer) {
    return canonical(inner).toPath().startsWith(canonical(outer).toPath());
  }

  /** A free name for a copy in the container: "Copy of x", "Copy (2) of x"... */
  static String getAutoNewNameFor(IContainer container, String name) {
    int counter = 1;
    while (true) {
      String n = counter > 1 ? "Copy (" + counter + ") of " + name : "Copy of " + name;
      if (container.findMember(n) == null)
        return n;
      counter++;
    }
  }

  /** The "Name Conflict" question: a new name for a copy in the same folder, or null when canceled. */
  private String askNewName(IContainer container, Source s) {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    int type = s.file.isDirectory() ? IResource.FOLDER : IResource.FILE;
    InputDialog dialog = new InputDialog(shell, "Name Conflict", "Enter a new name for '" + s.name() + "'",
        getAutoNewNameFor(container, s.name()), newText -> {
          IStatus st = workspace.validateName(newText, type);
          if (!st.isOK())
            return st.getMessage();
          if (container.findMember(newText) != null)
            return "'" + newText + "' already exists.";
          return null;
        });
    return dialog.open() == Window.OK ? dialog.getValue() : null;
  }

  /**
   * {@code CopyFilesAndFoldersOperation.validateDestination} (and {@code MoveFilesAndFoldersOperation}'s): why the
   * resources cannot be copied (moved) into the destination, or null.
   */
  public String validateDestination(IContainer destination, IResource[] sourceResources) {
    if (move) {
      IPath destinationLocation = destination.getLocation();
      for (IResource sourceResource : sourceResources) {
        // is the source being moved onto itself?
        if (sourceResource.getParent() != null && sourceResource.getParent().equals(destination))
          return "Cannot move '" + sourceResource.getName() + "'. The source and destination are the same.";
        if (destinationLocation != null) {
          IPath sourceLocation = sourceResource.getLocation();
          IPath destinationResource = destinationLocation.append(sourceResource.getName());
          if (sourceLocation != null && sourceLocation.isPrefixOf(destinationResource))
            return "Cannot move '" + sourceResource.getName() + "'. The source and destination are the same.";
        }
      }
    }
    if (!destination.isAccessible())
      return "Destination folder must be accessible.";
    IContainer firstParent = null;
    IPath destinationLocation = destination.getLocation();
    for (IResource sourceResource : sourceResources) {
      if (firstParent == null)
        firstParent = sourceResource.getParent();
      else if (!firstParent.equals(sourceResource.getParent()))
        return "The resources must have the same parent.";
      IPath sourceLocation = sourceResource.getLocation();
      if (sourceLocation == null || !sourceLocation.toFile().exists())
        return "The resource '" + sourceResource.getName() + "' does not exist on the file system.";
      if (destinationLocation != null) {
        if (sourceLocation.equals(destinationLocation))
          return "Cannot copy '" + sourceResource.getName() + "'. The source and destination are the same.";
        // is the source a parent of the destination?
        if (sourceLocation.isPrefixOf(destinationLocation))
          return "Destination cannot be a descendent of the source.";
      }
    }
    return null;
  }

  /** Copies (moves) the resources into the destination; the resources processed, after the user's answers. */
  public IResource[] copyResources(IResource[] resources, IContainer destination) {
    List<Source> sources = new ArrayList<>();
    for (IResource r : resources)
      if (r.getLocation() != null)
        sources.add(new Source(r.getLocation().toFile(), r));
    List<Source> done = run(sources, destination);
    List<IResource> r = new ArrayList<>();
    for (Source s : done)
      if (s.resource != null)
        r.add(s.resource);
    return r.toArray(new IResource[0]);
  }

  /** Copies files and folders of the file system (a FileTransfer, files dropped from outside) into the destination. */
  public void copyFiles(String[] fileNames, IContainer destination) {
    List<Source> sources = new ArrayList<>();
    for (String f : fileNames) {
      File file = new File(f);
      if (!file.exists())
        continue;
      IResource r = file.isDirectory()
          ? ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(Path.fromOSString(file.getAbsolutePath()))
          : ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(file.getAbsolutePath()));
      sources.add(new Source(file, r != null && r.exists() ? r : null));
    }
    run(sources, destination);
  }

  private List<Source> run(List<Source> sources, IContainer destination) {
    String title = move ? "Move Problems" : "Copy Problems";
    String verb = move ? "move" : "copy";
    File destDir = destination.getLocation() == null ? null : destination.getLocation().toFile();
    List<Source> done = new ArrayList<>();
    if (destDir == null)
      return done;
    List<Step> plan = new ArrayList<>();
    for (Source s : sources) {
      if (s.file.isDirectory() && isWithin(destDir, s.file)) {
        MessageDialog.openError(shell, title,
            "Cannot " + verb + " '" + s.name() + "'. The destination folder is a subfolder of the source folder.");
        return done;
      }
      File target = new File(destDir, s.name());
      if (canonical(s.file.getParentFile()).equals(canonical(destDir))) {
        if (move)
          continue; // moving into its own folder changes nothing
        String n = askNewName(destination, s);
        if (n == null)
          return done;
        plan.add(new Step(s, n, false));
      } else if (target.exists()) {
        if (isWithin(s.file, target)) {
          MessageDialog.openError(shell, title,
              "Cannot overwrite '" + target.getName() + "': it contains the resource being " + (move ? "moved." : "copied."));
          return done;
        }
        if (!MessageDialog.open(MessageDialog.QUESTION_WITH_CANCEL, shell, "Resource Exists",
            "'" + destination.getFullPath().append(s.name()).toString().substring(1) + "' exists.  Do you wish to overwrite?", 0))
          continue;
        plan.add(new Step(s, s.name(), true));
      } else {
        plan.add(new Step(s, s.name(), false));
      }
    }
    // The workspace resources are copied (moved) by an undoable operation of the workbench history, as Eclipse does;
    // the files of the file system are imported, which is not undoable.
    List<IResource> resourceSources = new ArrayList<>();
    List<IPath> targetPaths = new ArrayList<>();
    List<Step> systemFiles = new ArrayList<>();
    boolean renamed = false;
    for (Step step : plan) {
      if (step.source.resource != null) {
        resourceSources.add(step.source.resource);
        targetPaths.add(destination.getFullPath().append(step.targetName));
        renamed |= !step.targetName.equals(step.source.name());
      } else {
        systemFiles.add(step);
      }
    }
    String label = move ? "Move Resources" : "Copy Resources";
    final boolean renamedCopy = renamed;
    WorkspaceOperations.run(shell, move ? "Moving..." : "Copying...", title,
        move ? "Problems occurred moving the selected resources." : "Problems occurred copying the selected resources.", monitor -> {
          for (Step step : systemFiles) {
            if (monitor.isCanceled())
              return;
            File target = new File(destDir, step.targetName);
            try {
              mergeCopy(step.source.file, target);
              if (move)
                deleteTree(step.source.file);
            } catch (IOException e) {
              throw new CoreException(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES,
                  "Could not " + verb + " " + step.source.file + " to " + target, e));
            }
            done.add(step.source);
          }
          if (resourceSources.isEmpty() || monitor.isCanceled())
            return;
          IResource[] rs = resourceSources.toArray(new IResource[0]);
          AbstractWorkspaceOperation op;
          if (move)
            op = new MoveResourcesOperation(rs, destination.getFullPath(), label);
          else if (renamedCopy)
            op = new CopyResourcesOperation(rs, targetPaths.toArray(new IPath[0]), label);
          else
            op = new CopyResourcesOperation(rs, destination.getFullPath(), label);
          WorkspaceUndoUtil.execute(op, monitor, shell);
          for (Step step : plan)
            if (step.source.resource != null)
              done.add(step.source);
        });
    return done;
  }

  /** Copies a file or folder over the target: folders are merged, files replaced (copyExisting). */
  private static void mergeCopy(File from, File to) throws IOException {
    if (from.isDirectory()) {
      if (to.exists() && !to.isDirectory())
        Files.delete(to.toPath());
      Files.createDirectories(to.toPath());
      File[] children = from.listFiles();
      if (children != null)
        for (File c : children)
          mergeCopy(c, new File(to, c.getName()));
    } else {
      if (to.isDirectory())
        deleteTree(to);
      Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }
  }

  private static void deleteTree(File f) throws IOException {
    File[] children = f.isDirectory() ? f.listFiles() : null;
    if (children != null)
      for (File c : children)
        deleteTree(c);
    Files.deleteIfExists(f.toPath());
  }
}
