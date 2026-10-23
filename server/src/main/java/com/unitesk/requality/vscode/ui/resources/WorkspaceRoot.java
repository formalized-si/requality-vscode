package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * The workspace root. Its projects are the Requality projects found in the VS Code workspace folders, plus every
 * workspace folder that is not itself a Requality project (so files of the workspace can be browsed and picked as
 * in Eclipse) and projects created in this session.
 */
final class WorkspaceRoot extends FsContainer implements IWorkspaceRoot {
  private static final Map<String, File> CREATED = new LinkedHashMap<>();
  private final IWorkspace workspace;

  WorkspaceRoot(IWorkspace workspace) {
    super(Path.EMPTY);
    this.workspace = workspace;
  }

  static synchronized void created(ProjectRef p) {
    CREATED.put(p.getName(), p.toFile());
  }

  private static synchronized Map<String, File> createdProjects() {
    return new LinkedHashMap<>(CREATED);
  }

  /** Name → folder of every workspace project. */
  static Map<String, File> projectFolders() {
    Map<String, File> r = new LinkedHashMap<>();
    if (ServerContext.server() == null)
      return r;
    Workspace ws = ServerContext.server().workspace();
    for (Workspace.Project p : ws.projects())
      r.putIfAbsent(ProjectRef.handleName(p.dir), p.dir);
    for (Workspace.Project p : ws.otherProjects())
      r.putIfAbsent(ProjectRef.handleName(p.dir), p.dir);
    for (File f : ws.folders())
      if (!ws.isRemoved(f))
        r.putIfAbsent(ProjectRef.handleName(f), f);
    for (Map.Entry<String, File> e : createdProjects().entrySet())
      if (new File(e.getValue(), IProjectDescription.DESCRIPTION_FILE_NAME).isFile() && !ws.isRemoved(e.getValue()))
        r.putIfAbsent(e.getKey(), e.getValue());
    return r;
  }

  /** The file of a workspace full path, or null when its project is unknown. */
  static File fileOf(IPath full) {
    if (full.segmentCount() == 0)
      return null;
    File dir = projectFolders().get(full.segment(0));
    if (dir == null)
      return null;
    return new File(dir, full.removeFirstSegments(1).toOSString());
  }

  /** The project containing a file system location (the innermost one). */
  static ProjectRef projectOf(File file) {
    java.nio.file.Path p = file.getAbsoluteFile().toPath().normalize();
    ProjectRef best = null;
    int bestLen = -1;
    for (Map.Entry<String, File> e : projectFolders().entrySet()) {
      java.nio.file.Path d = e.getValue().getAbsoluteFile().toPath().normalize();
      if (p.startsWith(d) && d.getNameCount() > bestLen) {
        best = new ProjectRef(e.getKey(), e.getValue());
        bestLen = d.getNameCount();
      }
    }
    return best;
  }

  @Override
  public File toFile() {
    List<File> folders = ServerContext.server() == null ? new ArrayList<>() : ServerContext.server().workspace().folders();
    if (!folders.isEmpty())
      return folders.get(0);
    // No workspace folder yet (the trackers are created before the folders are set): the workspace root is the
    // server's workspace storage, where the trackers keep their global caches.
    return ServerContext.server() == null ? null : new File(ServerContext.server().options().storage.getAbsoluteFile(), "workspace");
  }

  @Override
  public String getName() {
    return "";
  }

  @Override
  public int getType() {
    return ROOT;
  }

  @Override
  public IPath getFullPath() {
    return Path.ROOT;
  }

  @Override
  public IProject getProject() {
    return null;
  }

  @Override
  public IContainer getParent() {
    return null;
  }

  @Override
  public IWorkspace getWorkspace() {
    return workspace;
  }

  @Override
  public boolean exists() {
    return true;
  }

  @Override
  public IResource[] members(int memberFlags) {
    return getProjects();
  }

  @Override
  public IProject[] getProjects() {
    List<IProject> r = new ArrayList<>();
    for (Map.Entry<String, File> e : projectFolders().entrySet())
      r.add(new ProjectRef(e.getKey(), e.getValue()));
    return r.toArray(new IProject[0]);
  }

  @Override
  public IProject[] getProjects(int memberFlags) {
    return getProjects();
  }

  /** A project handle; a project that does not exist yet is at the default location (Workspace.defaultLocation). */
  @Override
  public IProject getProject(String name) {
    File dir = projectFolders().get(name);
    if (dir != null)
      return new ProjectRef(name, dir);
    // the folder name of a project (the local name of its tree) names the same project
    if (ServerContext.server() != null)
      for (Workspace.Project p : ServerContext.server().workspace().projects())
        if (p.localName.equals(name))
          return new ProjectRef(ProjectRef.handleName(p.dir), p.dir);
    dir = ServerContext.server() == null ? null : ServerContext.server().workspace().defaultLocation(name);
    if (dir == null) {
      File base = toFile();
      dir = base == null ? null : new File(base, name);
    }
    return new ProjectRef(name, dir);
  }

  @Override
  public IResource findMember(IPath path) {
    if (path.segmentCount() == 0)
      return this;
    IProject p = getProject(path.segment(0));
    if (path.segmentCount() == 1)
      return p.exists() ? p : null;
    return p.findMember(path.removeFirstSegments(1));
  }

  @Override
  public IFile getFile(IPath path) {
    return getProject(path.segment(0)).getFile(path.removeFirstSegments(1));
  }

  @Override
  public IFolder getFolder(IPath path) {
    return getProject(path.segment(0)).getFolder(path.removeFirstSegments(1));
  }

  @Override
  public IFile getFileForLocation(IPath location) {
    File f = location.toFile();
    ProjectRef p = projectOf(f);
    if (p == null)
      return null;
    String rel = p.toFile().toPath().relativize(f.getAbsoluteFile().toPath().normalize()).toString();
    return rel.isEmpty() ? null : p.getFile(Path.fromOSString(rel));
  }

  @Override
  public IContainer getContainerForLocation(IPath location) {
    File f = location.toFile();
    ProjectRef p = projectOf(f);
    if (p == null)
      return null;
    String rel = p.toFile().toPath().relativize(f.getAbsoluteFile().toPath().normalize()).toString();
    return rel.isEmpty() ? p : p.getFolder(Path.fromOSString(rel));
  }

  @Override
  public IFile[] findFilesForLocationURI(URI location) {
    IFile f = getFileForLocation(Path.fromOSString(new File(location).getAbsolutePath()));
    return f == null ? new IFile[0] : new IFile[] { f };
  }

  @Override
  public IContainer[] findContainersForLocationURI(URI location) {
    IContainer c = getContainerForLocation(Path.fromOSString(new File(location).getAbsolutePath()));
    return c == null ? new IContainer[0] : new IContainer[] { c };
  }

  @Override
  public void delete(int updateFlags, IProgressMonitor monitor) throws CoreException {
    throw error("The workspace root cannot be deleted", null);
  }
}
