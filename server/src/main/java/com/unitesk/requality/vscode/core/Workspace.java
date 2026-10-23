package com.unitesk.requality.vscode.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;

/**
 * The Requality projects of the VS Code workspace. A project is a folder with a {@code .project} file declaring the
 * Requality catalog nature and a {@code root.json}, the same test the upstream trackers use. Projects are found in
 * the workspace folders themselves and in their sub-folders (Eclipse workspace layout), up to {@link #MAX_DEPTH},
 * and at the external locations of projects created, copied, moved or imported outside them. As in Eclipse, the
 * workspace remembers closed projects (listed, not loaded) and projects removed from it without deleting their
 * content (not listed until imported again), see {@link WorkspaceState}.
 */
public final class Workspace {
  public static final int MAX_DEPTH = 4;

  /** A discovered project. Keyed by folder name, which must be unique like Eclipse project names. */
  public static final class Project {
    public final File dir;
    public final String localName;
    /** A Requality catalog (else another Eclipse project of the workspace, shown with its files). */
    public final boolean requality;
    public volatile String error;
    /** Closed (IProject.close): listed, its tree is not loaded. */
    public volatile boolean closed;
    /**
     * The tracker is loading the project (EclipseTracker.handleNewProject has not returned). After a format conversion
     * the tree is ready before the tracking ends; the tracking then adds the undo hook of the tree
     * (AbstractResTracker.trackTreeDB), so the project is loaded only when the tracking ends, as the loading job of
     * Eclipse.
     */
    public volatile boolean loading;

    Project(File dir) {
      this(dir, true);
    }

    Project(File dir, boolean requality) {
      this.dir = dir;
      this.localName = dir.getName();
      this.requality = requality;
    }
  }

  private final RequalityServer server;
  /** The workspace folders (replaced as a whole when they change). */
  private volatile List<File> folders = new ArrayList<>();
  /** One discovery at a time: a scan and the application of its result are not interleaved with another scan. */
  private final Object scanLock = new Object();
  private final Map<String, Project> projects = new LinkedHashMap<>();
  /** The other Eclipse projects (a .project without the catalog nature): listed by the explorer, not loaded. */
  private final Map<String, Project> others = new LinkedHashMap<>();
  private final List<String> duplicates = new ArrayList<>();
  private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "requality-project-loader");
    t.setDaemon(true);
    return t;
  });
  private volatile boolean listening;
  private volatile WorkspaceState state = WorkspaceState.load(null);

  public Workspace(RequalityServer server) {
    this.server = server;
  }

  public void setFolders(List<String> paths) {
    setFolders(paths, null);
  }

  /** The workspace folders and the file keeping the workspace state (null: kept in memory only). */
  public void setFolders(List<String> paths, File stateFile) {
    if (stateFile != null)
      state = WorkspaceState.load(stateFile);
    com.unitesk.requality.vscode.ui.resources.BuildManager.start();
    List<File> fs = new ArrayList<>();
    for (String p : paths)
      fs.add(new File(p).getAbsoluteFile());
    folders = java.util.Collections.unmodifiableList(fs);
    if (!listening) {
      listening = true;
      CoreBootstrap.tracker().addListener(Moment.AFTER, new ITreeStateListener() {
        @Override
        public void open(TreeDB db) {
          changed();
        }

        @Override
        public void close(TreeDB db) {
          changed();
        }

        @Override
        public void delete(String localName, String projectName, TreeDB db) {
          changed();
        }

        @Override
        public void settingsChanged(TreeDB db) {
          changed();
        }
      });
    }
    rescan();
  }

  public List<File> folders() {
    return new ArrayList<>(folders);
  }

  /** Re-discovers projects; loads new ones and closes vanished ones. */
  public void rescan() {
    synchronized (scanLock) {
      scan();
    }
  }

  private void scan() {
    Map<String, File> found = new LinkedHashMap<>();
    Map<String, File> foundOthers = new LinkedHashMap<>();
    List<String> dups = new ArrayList<>();
    WorkspaceState st = state;
    for (File f : folders)
      discover(f, 0, found, foundOthers, dups, st);
    for (String ext : st.external()) {
      File d = new File(ext);
      if (st.isRemoved(d))
        continue;
      if (isRequalityProject(d))
        add(d, found, dups);
      else if (isEclipseProject(d))
        add(d, foundOthers, dups);
    }
    List<Project> toLoad = new ArrayList<>();
    List<Project> toUnload = new ArrayList<>();
    synchronized (this) {
      duplicates.clear();
      duplicates.addAll(dups);
      for (Project p : new ArrayList<>(projects.values())) {
        File f = found.get(p.localName);
        if (f == null || !f.equals(p.dir)) {
          projects.remove(p.localName);
          toUnload.add(p);
        }
      }
      for (Map.Entry<String, File> e : found.entrySet()) {
        if (projects.containsKey(e.getKey()))
          continue;
        Project p = new Project(e.getValue());
        p.closed = st.isClosed(p.dir);
        projects.put(p.localName, p);
        toLoad.add(p);
      }
      others.keySet().retainAll(foundOthers.keySet());
      for (Map.Entry<String, File> e : foundOthers.entrySet()) {
        Project old = others.get(e.getKey());
        if (old != null && old.dir.equals(e.getValue()))
          continue;
        Project p = new Project(e.getValue(), false);
        p.closed = st.isClosed(p.dir);
        others.put(p.localName, p);
      }
    }
    // Loading and closing run on the loader thread, in order, outside the workspace lock.
    for (Project p : toUnload)
      run(() -> unload(p));
    for (Project p : toLoad)
      if (!p.closed)
        run(() -> {
          autoBuild(p);
          load(p);
        });
    changed();
  }

  // ---- the project operations of the Eclipse workspace (IProject open/close/delete/copy/move)

  private Project byDir(File dir) {
    File a = dir.getAbsoluteFile();
    synchronized (this) {
      for (Map<String, Project> m : java.util.Arrays.asList(projects, others))
        for (Project p : m.values())
          if (p.dir.getAbsoluteFile().equals(a) || WorkspaceState.key(p.dir).equals(WorkspaceState.key(a)))
            return p;
    }
    return null;
  }

  /** True when the folder is one of the workspace folders or inside one. */
  public boolean isInWorkspaceFolders(File dir) {
    java.nio.file.Path d = new File(WorkspaceState.key(dir)).toPath();
    for (File f : folders)
      if (d.startsWith(new File(WorkspaceState.key(f)).toPath()))
        return true;
    return false;
  }

  /** The projects (catalogs and other Eclipse projects) in a folder and its sub-folders, in the workspace or not. */
  public List<File> findProjects(File root) {
    Map<String, File> found = new LinkedHashMap<>();
    Map<String, File> foundOthers = new LinkedHashMap<>();
    discover(root, 0, found, foundOthers, new ArrayList<>(), WorkspaceState.load(null));
    List<File> r = new ArrayList<>(found.values());
    r.addAll(foundOthers.values());
    return r;
  }

  /** The workspace project in a folder, or null. */
  public Project project(File dir) {
    return byDir(dir);
  }

  /** True when the project in the folder is closed. */
  public boolean isClosed(File dir) {
    Project p = byDir(dir);
    return p != null && p.closed;
  }

  /** IProject.close: the project stays listed, its tree is closed; remembered across sessions. */
  public void closeProject(File dir) throws Exception {
    Project p = byDir(dir);
    if (p == null || p.closed)
      return;
    state.setClosed(p.dir, true);
    p.closed = true;
    // On the calling thread: the tracker calls this from its own jobs (version update), which the loader thread may
    // be waiting for, so a load in progress must not be waited for here.
    if (p.requality)
      unload(p);
    changed();
  }

  /** IProject.open: the closed project is loaded again. */
  public void openProject(File dir) throws Exception {
    Project p = byDir(dir);
    if (p == null || !p.closed)
      return;
    state.setClosed(p.dir, false);
    p.closed = false;
    p.error = null;
    if (p.requality)
      run(() -> load(p));
    changed();
  }

  /**
   * IProject.delete: the project leaves the workspace once its tree is closed; without deleting its content it is
   * not discovered again until imported.
   */
  public void removeProject(File dir, boolean deleteContent) throws Exception {
    // On the loader thread, after a load in progress and before the loads queued meanwhile (which skip it): marked
    // removed first, so that no discovery finds it again, then unlisted and its tree closed.
    Project p;
    synchronized (scanLock) {
      state.removed(dir, false);
      p = byDir(dir);
      if (p != null)
        synchronized (this) {
          if (p.requality)
            projects.remove(p.localName);
          else
            others.remove(p.localName);
        }
    }
    if (p != null && p.requality)
      unload(p, true);
    changed();
  }

  /** True when the project in the folder was removed from the workspace (its content kept). */
  public boolean isRemoved(File dir) {
    return state.isRemoved(dir);
  }

  /** The two folders are the same location (canonical paths). */
  public static boolean sameLocation(File a, File b) {
    return a != null && b != null && WorkspaceState.key(a).equals(WorkspaceState.key(b));
  }

  /** The content of a removed project was deleted: nothing is remembered about its folder any more. */
  public void contentDeleted(File dir) {
    state.removed(dir, true);
  }

  /** A project folder that enters the workspace (created, copied, moved or imported there). */
  public void addProject(File dir) {
    state.added(dir, !isDiscoverable(dir));
    rescan();
  }

  /** The folder of a project moved (IProject.move with a new location). */
  public void projectMoved(File from, File to) {
    state.moved(from, to, !isDiscoverable(to));
  }

  /**
   * True when the discovery of the workspace folders finds a project in the folder: inside a workspace folder, at
   * most {@link #MAX_DEPTH} levels down, not under a hidden, node_modules or cache folder. Other locations are kept
   * as external locations.
   */
  public boolean isDiscoverable(File dir) {
    java.nio.file.Path d = new File(WorkspaceState.key(dir)).toPath();
    for (File f : folders) {
      java.nio.file.Path base = new File(WorkspaceState.key(f)).toPath();
      if (!d.startsWith(base))
        continue;
      java.nio.file.Path rel = base.relativize(d);
      if (rel.getNameCount() > MAX_DEPTH)
        continue;
      boolean hidden = false;
      for (java.nio.file.Path seg : rel) {
        String n = seg.toString();
        if (n.startsWith(".") || n.equals("node_modules") || n.equals(CoreUtils.CACHE_FOLDER_NAME))
          hidden = true;
      }
      if (!hidden)
        return true;
    }
    return false;
  }

  /** Waits for the tree of a project to be closed before its folder changes. */
  public void closeTree(File dir) throws Exception {
    Project p = byDir(dir);
    if (p != null)
      unload(p);
  }

  /**
   * The default location of a new project (Eclipse: the workspace root location): in the first workspace folder,
   * or next to it when that folder is itself a project (projects do not nest).
   */
  public File defaultLocation(String name) {
    File base = folders.isEmpty() ? null : folders.get(0);
    if (base == null)
      return null;
    if (new File(base, ".project").isFile() && base.getAbsoluteFile().getParentFile() != null)
      base = base.getAbsoluteFile().getParentFile();
    return new File(base, name);
  }

  public boolean isAutoBuilding() {
    return state.isAutoBuilding();
  }

  public void setAutoBuilding(boolean value) {
    state.setAutoBuilding(value);
  }

  /** True when the builders of the project ran (it has a build state). */
  public boolean isBuilt(File dir) {
    return state.isBuilt(dir);
  }

  public void setBuilt(File dir, boolean value) {
    state.setBuilt(dir, value);
  }

  /** The auto-build of a project entering the workspace: a project without a build state gets a full build. */
  private void autoBuild(Project p) {
    if (state.isAutoBuilding() && !state.isBuilt(p.dir))
      try {
        com.unitesk.requality.vscode.ui.resources.BuildManager.autoBuild(p.dir);
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
  }

  /** Runs on the loader thread and waits for it. */
  private void submit(Runnable r) throws Exception {
    try {
      loader.submit(r).get();
    } catch (java.util.concurrent.RejectedExecutionException e) {
      // shutting down
    }
  }

  /** Waits until all scheduled loads have run (tests). */
  public void awaitLoads() throws Exception {
    loader.submit(() -> {
    }).get();
  }

  private static void add(File dir, Map<String, File> found, List<String> duplicates) {
    File prev = found.get(dir.getName());
    if (prev != null && !prev.equals(dir))
      duplicates.add(dir.getAbsolutePath());
    else
      found.put(dir.getName(), dir);
  }

  private static void discover(File dir, int depth, Map<String, File> found, Map<String, File> foundOthers,
      List<String> duplicates, WorkspaceState st) {
    if (!dir.isDirectory())
      return;
    if (isRequalityProject(dir)) {
      if (!st.isRemoved(dir))
        add(dir, found, duplicates);
      return; // projects do not nest
    }
    // Another Eclipse project (a workspace folder itself is not taken as one); catalogs may be nested in it, as
    // nested projects in Eclipse.
    if (depth > 0 && isEclipseProject(dir) && !st.isRemoved(dir) && !found.containsKey(dir.getName()))
      add(dir, foundOthers, duplicates);
    if (depth >= MAX_DEPTH)
      return;
    File[] children = dir.listFiles(File::isDirectory);
    if (children == null)
      return;
    java.util.Arrays.sort(children);
    for (File c : children) {
      String n = c.getName();
      if (n.startsWith(".") || n.equals("node_modules") || n.equals(CoreUtils.CACHE_FOLDER_NAME))
        continue;
      discover(c, depth + 1, found, foundOthers, duplicates, st);
    }
  }

  /** An Eclipse project: a folder with a .project file. */
  public static boolean isEclipseProject(File dir) {
    return new File(dir, ".project").isFile();
  }

  /** Same criteria as {@code FilesTracker.reloadProjects}: catalog nature in .project and a root.json. */
  public static boolean isRequalityProject(File dir) {
    File dotProject = new File(dir, ".project");
    if (!dotProject.isFile() || !new File(dir, "root.json").isFile())
      return false;
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      org.w3c.dom.Document doc = f.newDocumentBuilder().parse(dotProject);
      NodeList natures = doc.getElementsByTagName("nature");
      for (int i = 0; i < natures.getLength(); i++) {
        if (Consts.CATALOG_NATURE.equals(((Element) natures.item(i)).getTextContent().trim()))
          return true;
      }
    } catch (Exception e) {
      RLogger.logInfo("Cannot read " + dotProject + ": " + e);
    }
    return false;
  }

  /** Runs a load or unload on the loader thread (nothing after shutdown). */
  private void run(Runnable r) {
    try {
      loader.execute(r);
    } catch (java.util.concurrent.RejectedExecutionException e) {
      // the server is shutting down
    }
  }

  /** Stops loading projects (server shutdown): queued loads are dropped, a running one is interrupted and awaited. */
  public void shutdown() {
    com.unitesk.requality.vscode.ui.resources.BuildManager.shutdown();
    loader.shutdownNow();
    try {
      loader.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // the trees of the projects are closed (not remembered as closed): the tracker releases their files, such as the
    // node cache database, which Windows does not let other programs delete or move while it is open
    for (Project p : projects())
      if (!p.closed)
        unload(p);
  }

  /** (Re)loads the workspace project in a folder (on the loader thread); false when the folder is not a project. */
  public boolean loadProject(File dir) {
    Project p;
    synchronized (this) {
      p = null;
      for (Project x : projects.values())
        if (x.dir.getAbsoluteFile().equals(dir.getAbsoluteFile()))
          p = x;
    }
    if (p == null)
      return false;
    Project fp = p;
    run(() -> load(fp));
    return true;
  }

  private void load(Project p) {
    EclipseTracker t = CoreBootstrap.tracker();
    if (t == null || p.closed)
      return;
    // a project removed (or replaced) after this load was queued is not loaded
    synchronized (this) {
      if (projects.get(p.localName) != p)
        return;
    }
    // A project the tracker has open (created by the tracker, or already loading) is not loaded twice; a tree that
    // can be (re)loaded (not loaded, canceled, restart) is loaded.
    TreeDB open = Nodes.findDB(p.localName);
    if (open != null && !open.isDisposed()) {
      TreeStateManager tsm = t.getTreeStateManager();
      if (!TreeState.isProjectLoadable(tsm.getState(p.localName, tsm.getRevisionInfo(open)))) {
        changed();
        return;
      }
    }
    p.loading = true;
    changed();
    try {
      t.handleNewProject(p.dir.toURI(), p.localName, false, false);
      // After a storage-format conversion the upstream tracker leaves the tree in RESTART and expects the
      // workspace to reopen the project (Eclipse does it from the resource delta).
      TreeDB db = Nodes.findDB(p.localName);
      TreeStateManager tsm = t.getTreeStateManager();
      TreeState st = db != null ? tsm.getState(p.localName, tsm.getRevisionInfo(db))
          : tsm.getState(p.localName, com.unitesk.requality.repo.RevisionInfo.NO_REV);
      if (st == TreeState.RESTART)
        t.handleNewProject(p.dir.toURI(), p.localName, false, false);
    } catch (Throwable e) {
      p.error = e.toString();
      RLogger.logError(e instanceof Exception ? (Exception) e : new RuntimeException(e));
    } finally {
      p.loading = false;
    }
    // The tree may have come ready without an open notification (after a version update): the explorer shows it.
    com.unitesk.requality.vscode.services.ExplorerService.refreshAllNodes();
    changed();
  }

  /**
   * Stops the tree of the project (the workspace keeps the project itself: a closed project is only unloaded). The
   * upstream {@code EclipseTracker.close(TreeDB)} would close the project, so the tree is stopped directly.
   */
  private void unload(Project p) {
    unload(p, false);
  }

  private void unload(Project p, boolean deleted) {
    TreeDB db = Nodes.findDB(p.localName);
    if (db != null) {
      try {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(p.localName);
        CoreBootstrap.tracker().stopTracking(project, db, null, p.localName, null, deleted);
      } catch (Throwable e) {
        RLogger.logError(e instanceof Exception ? (Exception) e : new RuntimeException(e));
      }
    }
  }

  public synchronized Project project(String localName) {
    return projects.get(localName);
  }

  public synchronized List<Project> projects() {
    return new ArrayList<>(projects.values());
  }

  /** The other Eclipse projects of the workspace (not Requality catalogs). */
  public synchronized List<Project> otherProjects() {
    return new ArrayList<>(others.values());
  }

  /** A project of either kind by its folder name. */
  public synchronized Project anyProject(String localName) {
    Project p = projects.get(localName);
    return p != null ? p : others.get(localName);
  }

  /** Project list for the extension. */
  public JsonObject describe() {
    List<Project> ps;
    List<String> dupsCopy;
    synchronized (this) {
      ps = new ArrayList<>(projects.values());
      dupsCopy = new ArrayList<>(duplicates);
    }
    JsonArray arr = new JsonArray();
    EclipseTracker t = CoreBootstrap.tracker();
    for (Project p : ps) {
      JsonObject o = new JsonObject();
      o.addProperty("localName", p.localName);
      o.addProperty("path", p.dir.getAbsolutePath());
      String pname = CoreUtils.getProjectName(p.dir);
      o.addProperty("name", pname != null ? pname : p.localName);
      TreeDB db = Nodes.findDB(p.localName);
      String state;
      if (p.closed) {
        state = "closed";
      } else if (p.error != null || isBroken(p.dir)) {
        state = "broken";
      } else if (db == null || t == null) {
        state = "loading";
        if (t != null)
          o.addProperty("treeState", String.valueOf(t.getTreeStateManager().getState(p.localName,
              com.unitesk.requality.repo.RevisionInfo.NO_REV)));
      } else {
        TreeStateManager tsm = t.getTreeStateManager();
        TreeState ts = tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db));
        ProjectLoadingState pls = t.getProjectLoadingState(p.dir.toURI(), db.getSemiProjectName());
        if (TreeState.isDisposed(ts))
          state = "closed";
        else if (TreeState.readyState(ts) || ts == TreeState.MODIFYING)
          state = pls == ProjectLoadingState.VERSION_UPDATE || p.loading ? "loading" : "ready";
        else
          state = "loading";
        o.addProperty("rootId", Nodes.id(db.getRootNode()));
        o.addProperty("treeState", String.valueOf(ts));
      }
      o.addProperty("state", state);
      if (p.error != null)
        o.addProperty("error", p.error);
      arr.add(o);
    }
    JsonObject r = new JsonObject();
    r.add("projects", arr);
    JsonArray dups = new JsonArray();
    dupsCopy.forEach(dups::add);
    r.add("duplicates", dups);
    return r;
  }

  private static boolean isBroken(File dir) {
    try {
      String content = new String(java.nio.file.Files.readAllBytes(new File(dir, ".project").toPath()),
          java.nio.charset.StandardCharsets.UTF_8);
      return content.contains(Consts.CATALOG_BROKEN_NATURE);
    } catch (IOException e) {
      return false;
    }
  }

  private void changed() {
    server.client().notify("workspace/projectsChanged", describe());
  }
}
