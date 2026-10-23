package com.unitesk.requality.vscode.ui.resources;

import java.io.File;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.vscode.core.CoreBootstrap;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * A project of the VS Code workspace (Eclipse {@code IProject}): a folder with a {@code .project} file. Requality
 * projects are named by their folder, which is also the local name of their tree.
 */
public final class ProjectRef extends Project implements IProject {
  private final String name;
  private volatile File dir;
  private final TreeDB db;

  public ProjectRef(String name, File dir) {
    super(Path.EMPTY);
    this.name = name;
    this.dir = dir == null ? null : dir.getAbsoluteFile();
    this.db = null;
  }

  /** The project of a loaded tree. */
  public ProjectRef(TreeDB db, File dir) {
    super(Path.EMPTY);
    this.dir = dir == null ? null : dir.getAbsoluteFile();
    this.name = dir != null ? handleName(dir) : db.getSemiLocalName();
    this.db = db;
  }

  /**
   * The name of the project handle, as IProject.getName: the name of its description (.project), which is what the
   * Eclipse project is named after. The folder is the local name of its tree (see {@link #getLocalName}); a folder
   * without a readable description is named after the folder.
   */
  public static String handleName(File dir) {
    if (dir == null)
      return null;
    File d = dir.getAbsoluteFile();
    String fallback = d.getName();
    File f = new File(d, IProjectDescription.DESCRIPTION_FILE_NAME);
    if (!f.isFile())
      return fallback;
    // read again only when the description changed (its time and size), without the core's name cache
    String stamp = f.lastModified() + ":" + f.length();
    String[] cached = NAMES.get(f.getPath());
    if (cached == null || !cached[0].equals(stamp)) {
      cached = new String[] { stamp, readName(f) };
      NAMES.put(f.getPath(), cached);
    }
    return cached[1] == null || cached[1].isEmpty() ? fallback : cached[1];
  }

  /** The handle names of the description files read, by path: { time and size of the file, name }. */
  private static final java.util.Map<String, String[]> NAMES = new java.util.concurrent.ConcurrentHashMap<>();

  /** The name element of a .project file (a direct child of the root), or null when it cannot be read. */
  private static String readName(File dotProject) {
    try {
      javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      org.w3c.dom.Element root = f.newDocumentBuilder().parse(dotProject).getDocumentElement();
      for (org.w3c.dom.Node n = root.getFirstChild(); n != null; n = n.getNextSibling())
        if (n instanceof org.w3c.dom.Element && "name".equals(n.getNodeName()))
          return n.getTextContent().trim();
    } catch (Exception e) {
      com.unitesk.requality.core.log.RLogger.logInfo("Cannot read the name of " + dotProject + ": " + e);
    }
    return null;
  }

  /** The folder name of the project: the local name of its tree (IProject.getLocation().lastSegment()). */
  public String getLocalName() {
    return dir == null ? name : dir.getName();
  }

  @Override
  public File toFile() {
    return dir;
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * The name of the project as Eclipse shows it: the name of its description (.project). The handle name is the
   * folder name, which is the local name of the Requality tree.
   */
  public static String displayName(IProject p) {
    File dir = p.getLocation() == null ? null : p.getLocation().toFile();
    String n = dir == null ? null : com.unitesk.requality.tool.CoreUtils.getProjectName(dir);
    return n == null ? p.getName() : n;
  }

  @Override
  public int getType() {
    return PROJECT;
  }

  @Override
  public IPath getFullPath() {
    return Path.ROOT.append(name);
  }

  @Override
  public IProject getProject() {
    return this;
  }

  @Override
  public IContainer getParent() {
    return ResourcesPlugin.getWorkspace().getRoot();
  }

  @Override
  public TreeDB getTreeDB() {
    if (db != null && !db.isDisposed())
      return db;
    return Nodes.findDB(getLocalName());
  }

  /** A project of the workspace: a folder with a .project that was not removed from the workspace. */
  @Override
  public boolean exists() {
    if (dir == null || !new File(dir, IProjectDescription.DESCRIPTION_FILE_NAME).isFile())
      return false;
    Workspace ws = workspace();
    return ws == null || !ws.isRemoved(dir);
  }

  /** IProject.isOpen: an existing project the workspace has not closed. */
  @Override
  public boolean isOpen() {
    if (!exists())
      return false;
    Workspace ws = workspace();
    return ws == null || !ws.isClosed(dir);
  }

  private static Workspace workspace() {
    return ServerContext.server() == null ? null : ServerContext.server().workspace();
  }

  /**
   * Eclipse keeps a project at its default location (the workspace root folder, here the first workspace folder, see
   * {@link Workspace#defaultLocation}) in a folder named as the project (.project name); a project in a folder of
   * another name, and a workspace folder that is itself a project, are at a custom location (a rename keeps the
   * folder).
   */
  public boolean isAtDefaultLocation() {
    Workspace ws = workspace();
    if (ws == null || dir == null || !exists())
      return false;
    File def = ws.defaultLocation(displayName(this));
    if (def == null || !Workspace.sameLocation(def, dir))
      return false;
    for (File f : ws.folders())
      if (Workspace.sameLocation(f, dir))
        return false;
    return true;
  }

  @Override
  public boolean isAccessible() {
    return isOpen();
  }

  @Override
  public boolean hasNature(String natureId) throws CoreException {
    return exists() && getDescription().hasNature(natureId);
  }

  @Override
  public boolean isNatureEnabled(String natureId) throws CoreException {
    return hasNature(natureId);
  }

  @Override
  public IProjectDescription getDescription() throws CoreException {
    if (!exists())
      throw error("Project does not exist: " + name, null);
    ProjectDescription d = ProjectDescription.read(new File(dir, IProjectDescription.DESCRIPTION_FILE_NAME));
    // Project.getDescription: the location of a project at its default location is null.
    if (isAtDefaultLocation())
      d.setLocationURI(null);
    return d;
  }

  @Override
  public void setDescription(IProjectDescription description, IProgressMonitor monitor) throws CoreException {
    setDescription(description, NONE, monitor);
  }

  @Override
  public void setDescription(IProjectDescription description, int updateFlags, IProgressMonitor monitor)
      throws CoreException {
    ProjectDescription d = (ProjectDescription) description;
    if (d.getName() == null || d.getName().isEmpty())
      d.setName(name);
    File file = new File(dir, IProjectDescription.DESCRIPTION_FILE_NAME);
    String[] oldNatures = file.isFile() ? ProjectDescription.read(file).getNatureIds() : new String[0];
    d.write(file);
    // Project.setDescription: the natures added or removed by the new description are (de)configured.
    NatureManager.configureNatures(this, oldNatures, d.getNatureIds());
    rescan();
  }

  @Override
  public void move(IProjectDescription description, boolean force, IProgressMonitor monitor) throws CoreException {
    move(description, force ? FORCE : NONE, monitor);
  }

  /**
   * IProject.move: the project gets the name and location of the description; a null location is the default
   * location of the new name (so a project at its default location is renamed with its folder, as in Eclipse), a
   * project at a custom location keeps its folder unless the description gives another one.
   */
  @Override
  public void move(IProjectDescription description, int updateFlags, IProgressMonitor monitor) throws CoreException {
    ProjectDescription d = (ProjectDescription) description;
    String newName = d.getName() == null || d.getName().isEmpty() ? name : d.getName();
    Workspace ws = workspace();
    File to;
    if (d.getLocationURI() != null)
      to = new File(d.getLocationURI()).getAbsoluteFile();
    else if (ws != null && ws.defaultLocation(newName) != null)
      to = ws.defaultLocation(newName).getAbsoluteFile();
    else
      to = dir;
    File from = dir;
    ProjectRef before = new ProjectRef(name, from);
    if (!to.equals(from.getAbsoluteFile())) {
      checkTarget(ws, from, to, "move");
      if (ws != null)
        try {
          ws.closeTree(from);
        } catch (Exception e) {
          throw error("Could not close the tree of " + name, e);
        }
      try {
        java.nio.file.Files.createDirectories(to.toPath().getParent());
        try {
          FileMoves.move(from.toPath(), to.toPath());
        } catch (java.io.IOException e) {
          // another file system: a folder is moved by copying it and deleting the original
          copyTree(from, to);
          removeTree(from);
        }
      } catch (Exception e) {
        if (ws != null) {
          if (from.exists()) {
            // the project stays where it was: its tree is loaded again
            rescanAndReload(ws, from);
          } else if (to.exists()) {
            // the folder did move (copied, the original only partly deleted)
            dir = to;
            ws.projectMoved(from, to);
            ws.rescan();
          }
        }
        throw error("Could not move " + from + " to " + to, e);
      }
      dir = to;
      if (ws != null)
        ws.projectMoved(from, to);
      // the markers of a project are keyed by its folder name; its persistent properties by location
      if (!name.equals(newName))
        MarkerStore.moved(Path.ROOT.append(name), Path.ROOT.append(newName));
      ResourceProperties.moved(from, to, false);
    }
    d.setLocationURI(null);
    d.write(new File(dir, IProjectDescription.DESCRIPTION_FILE_NAME));
    rescan();
    if (!to.equals(from.getAbsoluteFile()) || !newName.equals(before.name))
      ResourceChanges.post(before, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO,
          Path.ROOT.append(newName));
  }

  /**
   * The checks of a new location of the project's content: not inside the project itself, not existing, and (as
   * projects are known by their folder name) not named like the folder of another project.
   */
  private void checkTarget(Workspace ws, File from, File to, String verb) throws CoreException {
    java.nio.file.Path f = canonical(from), t = canonical(to);
    if (t.startsWith(f))
      throw error("Could not " + verb + " " + name + ": " + to + " is inside the project", null);
    if (to.exists())
      throw error("Could not " + verb + " " + name + ": " + to + " already exists", null);
    if (ws != null) {
      Workspace.Project other = ws.anyProject(to.getName());
      if (other != null && !canonical(other.dir).equals(f))
        throw error("A project with the folder name '" + to.getName() + "' already exists in the workspace", null);
    }
  }

  private static java.nio.file.Path canonical(File f) {
    try {
      return f.getCanonicalFile().toPath();
    } catch (java.io.IOException e) {
      return f.getAbsoluteFile().toPath().normalize();
    }
  }

  private static void rescanAndReload(Workspace ws, File dir) {
    try {
      ws.rescan();
      ws.loadProject(dir);
    } catch (RuntimeException e) {
      com.unitesk.requality.core.log.RLogger.logError(e);
    }
  }

  private static void copyTree(File from, File to) throws java.io.IOException {
    java.nio.file.Path src = from.toPath();
    try (java.util.stream.Stream<java.nio.file.Path> st = java.nio.file.Files.walk(src)) {
      for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) st::iterator) {
        java.nio.file.Path t = to.toPath().resolve(src.relativize(p).toString());
        if (java.nio.file.Files.isDirectory(p))
          java.nio.file.Files.createDirectories(t);
        else
          java.nio.file.Files.copy(p, t, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
      }
    }
  }

  private static void removeTree(File f) throws java.io.IOException {
    if (!f.exists())
      return;
    try (java.util.stream.Stream<java.nio.file.Path> st = java.nio.file.Files.walk(f.toPath())) {
      for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) st.sorted(java.util.Comparator.reverseOrder())::iterator)
        java.nio.file.Files.deleteIfExists(p);
    }
  }

  /**
   * IResource.copy of a project: the content is copied into a hidden folder next to the target, which is renamed to
   * the target once complete (so that the workspace never discovers a half-copied project).
   */
  @Override
  public void copy(IProjectDescription description, int updateFlags, IProgressMonitor monitor) throws CoreException {
    ProjectDescription d = (ProjectDescription) description;
    String newName = d.getName();
    Workspace ws = workspace();
    File to = d.getLocationURI() != null ? new File(d.getLocationURI()).getAbsoluteFile()
        : ws != null ? ws.defaultLocation(newName) : null;
    if (to == null)
      throw error("No location for the copy of " + name, null);
    checkTarget(ws, dir, to, "copy");
    File tmp = new File(to.getAbsoluteFile().getParentFile(), "." + to.getName() + ".copying");
    try {
      removeTree(tmp);
      java.nio.file.Files.createDirectories(tmp.toPath().getParent());
      copyTree(dir, tmp);
      d.setLocationURI(null);
      d.write(new File(tmp, IProjectDescription.DESCRIPTION_FILE_NAME));
      FileMoves.move(tmp.toPath(), to.toPath());
    } catch (java.io.IOException | CoreException e) {
      try {
        removeTree(tmp);
      } catch (java.io.IOException e2) {
        // nothing more to clean
      }
      throw error("Could not copy " + dir + " to " + to, e);
    }
    // the copy gets the persistent properties of the project's resources
    ResourceProperties.moved(dir, to, true);
    if (ws != null)
      ws.addProject(to);
  }

  @Override
  public void create(IProgressMonitor monitor) throws CoreException {
    create(null, monitor);
  }

  @Override
  public void create(IProjectDescription description, IProgressMonitor monitor) throws CoreException {
    if (exists())
      throw error("Project already exists: " + name, null);
    ProjectDescription d = description == null ? new ProjectDescription() : (ProjectDescription) description;
    if (d.getLocationURI() != null)
      dir = new File(d.getLocationURI()).getAbsoluteFile();
    if (dir == null)
      throw error("No location for project " + name, null);
    if (d.getName() == null || d.getName().isEmpty())
      d.setName(name);
    if (!dir.isDirectory() && !dir.mkdirs())
      throw error("Could not create " + dir, null);
    d.write(new File(dir, IProjectDescription.DESCRIPTION_FILE_NAME));
    WorkspaceRoot.created(this);
    // The natures of the creation description are configured (their builders are added to the description).
    NatureManager.configureNatures(this, new String[0], d.getNatureIds());
    Workspace ws = workspace();
    if (ws != null && ServerContext.server().isInitialized())
      ws.addProject(dir);
  }

  @Override
  public void open(IProgressMonitor monitor) throws CoreException {
    open(NONE, monitor);
  }

  /** IProject.open: a closed project of the workspace is opened (its tree loaded). */
  @Override
  public void open(int updateFlags, IProgressMonitor monitor) throws CoreException {
    Workspace ws = workspace();
    if (ws != null && dir != null && ws.isClosed(dir)) {
      try {
        ws.openProject(dir);
      } catch (Exception e) {
        throw error("Could not open " + name, e);
      }
      ResourceChanges.post(this, IResourceDelta.CHANGED, IResourceDelta.OPEN, null);
      return;
    }
    rescan();
  }

  /** IProject.close: the workspace keeps the project closed (its tree is closed, it is not loaded). */
  @Override
  public void close(IProgressMonitor monitor) throws CoreException {
    Workspace ws = workspace();
    if (ws != null && dir != null && ws.project(dir) != null) {
      try {
        ws.closeProject(dir);
      } catch (Exception e) {
        throw error("Could not close " + name, e);
      }
      ResourceChanges.post(this, IResourceDelta.CHANGED, IResourceDelta.OPEN, null);
      return;
    }
    TreeDB t = getTreeDB();
    IResTracker tracker = CoreBootstrap.tracker();
    if (t != null && tracker != null)
      tracker.close(t);
  }

  @Override
  public void build(int kind, IProgressMonitor monitor) throws CoreException {
    BuildManager.build(this, kind, monitor);
  }

  @Override
  public IProject[] getReferencedProjects() throws CoreException {
    return exists() ? getDescription().getReferencedProjects() : new IProject[0];
  }

  @Override
  public void delete(boolean deleteContent, boolean force, IProgressMonitor monitor) throws CoreException {
    delete((deleteContent ? ALWAYS_DELETE_PROJECT_CONTENT : NEVER_DELETE_PROJECT_CONTENT) | (force ? FORCE : NONE),
        monitor);
  }

  /**
   * IProject.delete: the project leaves the workspace; its content is deleted with ALWAYS_DELETE_PROJECT_CONTENT,
   * kept with NEVER_DELETE_PROJECT_CONTENT and, without either flag, deleted when the project is open.
   */
  @Override
  public void delete(int updateFlags, IProgressMonitor monitor) throws CoreException {
    boolean content = (updateFlags & ALWAYS_DELETE_PROJECT_CONTENT) != 0
        || (updateFlags & NEVER_DELETE_PROJECT_CONTENT) == 0 && isOpen();
    Workspace ws = workspace();
    try {
      if (ws != null)
        ws.removeProject(dir, content);
      else
        close(monitor);
    } catch (Exception e) {
      throw error("Could not delete " + name, e);
    }
    if (content) {
      super.delete(updateFlags, monitor);
      if (ws != null)
        ws.contentDeleted(dir);
    } else {
      // the project leaves the workspace with its metadata (markers, persistent properties)
      MarkerStore.delete(this, null, false, DEPTH_INFINITE);
      ResourceProperties.deleted(dir);
    }
    rescan();
    ResourceChanges.post(this, IResourceDelta.REMOVED, 0, null);
  }

  private static void rescan() {
    if (ServerContext.server() != null && ServerContext.server().isInitialized())
      ServerContext.server().workspace().rescan();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProjectRef))
      return false;
    ProjectRef p = (ProjectRef) o;
    // Same name (the hash) and, when both are located, the same folder.
    return name.equals(p.name) && (dir == null || p.dir == null || dir.equals(p.dir));
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "P/" + name;
  }
}
