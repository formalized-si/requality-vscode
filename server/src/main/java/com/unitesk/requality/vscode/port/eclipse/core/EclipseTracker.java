/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.vscode.port.eclipse.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.vscode.ui.resources.ResourceException;
import com.unitesk.requality.vscode.ui.resources.Workspace;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeEvent;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeListener;
import com.unitesk.requality.vscode.ui.resources.IResourceDelta;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.IncrementalProjectBuilder;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.CompositeChange;
import com.unitesk.requality.vscode.ui.ltk.Refactoring;
import com.unitesk.requality.vscode.ui.ltk.RefactoringContribution;
import com.unitesk.requality.vscode.ui.ltk.RefactoringCore;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.ltk.resource.RenameResourceChange;
import com.unitesk.requality.vscode.ui.ltk.resource.RenameResourceDescriptor;
import com.unitesk.requality.vscode.ui.team.RepositoryProvider;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.descriptors.NodeTypeDescriptor;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.AbstractResTracker;
import com.unitesk.requality.core.storage.CPLinkResolver;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.DBLocalName;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.TreeEvent;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.formatversion.StoredFormatVersioning;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.handlers.ProjectRenameChange;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class EclipseTracker extends AbstractResTracker {
  private static final long RENAME_STATE_TIMEOUT = 300000;
  public static final String REQ_CATALOG_NATURE = Consts.CATALOG_NATURE;
  public static final String NON_LOADABLE_NATURE = Consts.CATALOG_BROKEN_NATURE;
  private static final int NEW_PROJET_TIMEOUT = 500; // Timeoout for actual project loading
  private static boolean ignoreGUI = false;
  private static boolean updateAutoOk = true;
  private Map<URI, Long> timeoutToLoad = new ConcurrentHashMap<>();
  private final ReentrantLock projectLoadingLock = new ReentrantLock();

  public static void setNoMsgMode(boolean nmm) {
    ignoreGUI = nmm;
  }

  public static void setOkOnProjectVersionUpdates(boolean okpvu) {
    updateAutoOk = okpvu;
  }

  private Map<IProject, TreeDB> treeByProject = new ConcurrentHashMap<>();
  private DBNamesManager dbnm = null;
  // private Map<TreeDB, IProject> projectByTree = new ConcurrentHashMap<>();
  private Map<IProject, ResourceStorage> projectToStorage = new ConcurrentHashMap<>();

  private static EclipseTracker tracker;

  private boolean initedNames = false;
  private Map<String, IProject> lProjByName = null;

  private void initNames() {
    initedNames = true;
    lProjByName = new HashMap<>();

    IWorkspaceRoot wr = ResourcesPlugin.getWorkspace().getRoot();
    for (IProject p : wr.getProjects())
      if (p.isOpen() && p.isAccessible())
        registerNames(p);
  }

  private void registerNames(IProject p) {
    if (!initedNames)
      initNames();
    IPath loc = p.getLocation();
    if (loc == null) {
      RLogger.logError("Try to register a project without a location");
      return;
    }
    String lname = p.getLocation().lastSegment();
    String pname = getProjectName(p);
    lProjByName.put(lname, p);
    dbnm.register(DBLocalName.formLocalNameKey(lname, RevisionInfo.toString(null, null)), pname);
  }

  public void unRegisterNames(IProject p, String lname) {
    if (!initedNames)
      initNames();
    if (p != null) {
      String slname = getLocalName(p);
      if (slname != null)
        lProjByName.remove(slname);
      else if (lname != null)
        lProjByName.remove(lname);
    }
  }

  private EclipseTracker(DBNamesManager dbnm) {
    super();
    this.dbnm = dbnm;
    setPath(CoreUtils.getCacheDir(getGlobalCacheLocation()));
    if (!initedNames)
      initNames();
    ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener);
  }

  private static TreeState[] UNTRACK_STATES = new TreeState[] { TreeState.HALTED };

  public static EclipseTracker getTracker(DBNamesManager dbnm) {
    if (tracker == null) {
      tracker = new EclipseTracker(dbnm);
      tracker.getTreeStateManager().addStateHook("*", UNTRACK_STATES, tracker.stateChangeHandler);
      // tracker.addsHaltTrackerReaction();
    }
    return tracker;
  }

  private IStateChangeHandler stateChangeHandler = new AAllTreeStateChangeHandler() {
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState,
        TreeState state) {
      IProject p = db.getMetaObject(META_ECLIPSE_PROJECT);
      if (p != null) {
        stopTracking(p, db, null, lname, pname, false); // TODO gather deleted state?
      }
    }
  };
  private static String META_ECLIPSE_PROJECT = "RELATED_ECLPSE_PROJECT";

  public IProject getProject(TreeDB db) {
    if (db == null)
      return null;
    IProject proj = db.getMetaObject(META_ECLIPSE_PROJECT);
    if (proj == null && db.isVirtual()) {
      TreeDB root = Instantiator.getRootTree(db);
      if (root != db)
        return getProject(root);
    }
    return proj;
  }

  private TreeDB getTreeByProject(IProject proj) {
    if (proj == null)
      return null;
    return treeByProject.get(proj);
  }

  private void closeProject(final IProject project) {
    while (project.getWorkspace().isTreeLocked())
      try {
        this.wait(200);
      } catch (Exception e) {
        RLogger.logError(e);
      }
    try {
      if (project.exists())
        project.close(null);
      uriCache.remove(project);
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
  }

  private ReentrantLock rl = new ReentrantLock();

  @Override
  public TreeDB loadTreeDBDescription(Object obj, RevisionInfo ri, String lname,
      com.unitesk.requality.core.operation.IProgressMonitor mon) {
    if (obj instanceof IProject)
      try {
        return loadTreeDBDescription(getUri((IProject) obj, true), ri, lname, mon);
      } catch (IOException e1) {
        e1.printStackTrace();
      }
    if (obj instanceof URI) {
      try {
        return loadTreeDBDescription((URI) obj, ri, lname, mon);
      } catch (IOException e) {
        RLogger.logError("Error during looking for project by uri " + obj);
        RLogger.logError(e);
      }
    }
    if (obj instanceof File)
      try {
        return loadTreeDBDescription(((File) obj).toURI(), ri, lname, mon);
      } catch (IOException e) {
        RLogger.logError("Error during looking for project by file " + obj);
        RLogger.logError(e);
      }
    return null;
  }

  @Override
  public void initDBLoading(Object proj, RevisionInfo ri, TreeDB db) {
    try {
      TreeStateManager.getOne(db.getTracker()).setRevisionInfo(db, ri);
    } finally {
    }
    IProject p = extractProject(proj);
    if (p != null)
      markCacheFolder(p);
  }

  @Override
  public boolean isOpen(Object project) {
    IProject p = extractProject(project);
    if (p != null)
      return p.isOpen();
    return false;
  }

  @Override
  public void close(Object project) {
    IProject p = extractProject(project);
    if (p != null)
      closeProject(p);
  }

  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, String name) throws IOException {
    return getTreeDB(path, ri, false, false, this);
  }

  @Override
  public void markNonLoadable(URI uri) {
    IProject p;
    try {
      p = getProject(uri);
      if (p != null)
        markNonLoadable(p);
    } catch (CoreException e) {
      RLogger.logError(e);
    }
  }

  public static void markNonLoadable(IProject p) {
    if (!p.isOpen())
      try {
        p.open(null);
      } catch (CoreException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
    IProjectDescription description;
    try {
      description = p.getDescription();
      IFile nf = p.getFile(".project.back");
      IFile f = p.getFile(".project");
      if (nf.exists())
        nf.delete(true, null);
      if (f.exists())
        f.copy(nf.getFullPath(), true, null);
      String[] natures = description.getNatureIds();
      String[] newNatures = new String[natures.length + 1];
      int i = 0;
      for (String s : natures)
        newNatures[i++] = s;
      newNatures[natures.length] = NON_LOADABLE_NATURE;
      description.setNatureIds(newNatures);
      String name = p.getName();
      try{
        name = p.getDescription().getName();
      }catch(CoreException ce){
      }
      WorkspaceJob wj = new WorkspaceJob("Mark project " + name + " as non loadable") {
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
          p.setDescription(description, new NullProgressMonitor());
          return Status.OK_STATUS;
        }
      };
      wj.schedule();
    } catch (CoreException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    ;
  }

  private IFolder markCacheFolder(IProject project) {
    ResourceAttributes ra = new ResourceAttributes();
    ra.setHidden(true);
    ra.setSymbolicLink(true);
    IFolder f = project.getFolder(CoreUtils.CACHE_FOLDER_NAME);
    if (f != null) {
      try {
        if (!f.exists()) {
          f.create(true, true, null);
          if (f.exists() && !f.isDerived())
            f.setDerived(true, null);
          f.refreshLocal(IResource.DEPTH_INFINITE, null);
        }
        f.setTeamPrivateMember(true);
        f.setResourceAttributes(ra);
        f.setDerived(true, null);
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return null;
  }

  public static File toFile(IProject project) {
    return project.getLocation().toFile();
  }

  @Override
  public void initClasses(TreeDB db) {
    Map<String, NodeTypeDescriptor> nts = RequalityPlugin.getAdditionalTypes();
    for (String name : RequalityPlugin.getAdditionalTypes().keySet())
      db.addNodeType(name, nts.get(name).getClazz());
  }

  public boolean stopTracking(IProject project, TreeDB db, URI uri, String lname, String pnameReq, boolean deleted) {
    try {
      RLogger.logDebug("before stopTracking: " + lname + " " + getTreeStateManager().getState(lname, null), 3);
      RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()), 3);
      RLogger.logDebug("before stopTracking: " + lname + " " + getProjectLoadingState(uri), 3);
      String name = lname, pname = pnameReq;
      CacheStorage cs = null;
      try {
        if (uri == null)
          uri = getUri(project, true); // here we use a cached url
        if ((!isTracking(project) && !treeByProject.containsKey(project)) || project == null) {
          return false;
        }
        if (uri == null)
          return false;
        boolean isLatest = false;
        if (db == null)
          try {
            db = getTreeDB(project, null, name);
            cs = db.getStorage().getCacheStorage();
            isLatest = true;
          } catch (Exception e) {
            RLogger.logError(e);
          }
        else {
          cs = db.getStorage().getCacheStorage();
          isLatest = treeByProject.get(project) == db;
        }
        if (db == null)
          return false;
        boolean wasDis = db.isDisposed();
        if (pnameReq == null)
          pname = db.getProjectName();
        boolean isVersUpdate = getProjectLoadingState(uri, pname) == ProjectLoadingState.VERSION_UPDATE;
        // disallow to drop caches during version update
        ITreeStorage ts = db.getStorage();
        ResourceStorage storage = null;
        if (ts instanceof TransactionStorage) {
          storage = (ResourceStorage) ((TransactionStorage) ts).getStorage();
          if (!isVersUpdate) {
            ((TransactionStorage) ts).rollback();
            ts = ((TransactionStorage) ts).getStorage();
          }
        } else
          storage = (ResourceStorage) ts;
        storage.forceNow = true; // avoid creating jobs
        try {
          ResourcesPlugin.getWorkspace().removeResourceChangeListener(storage.getResourceChangeListener());
          if (isVersUpdate)
            return false;

          // Global.onlyClearCaches(db, deleted, false);
          // tends to be duplicate of notify
          // ResourceStorage storage = (ResourceStorage) ts;

          if (!deleted) {
            notifyListeners(Moment.BEFORE, name, pname, db, TreeEvent.TREE_CLOSE);
          } else {
            try {
              ResManager.markTreeAsDeleting(db);
              RepositoryProvider prov = RepositoryProvider.getProvider(project);
              if (prov != null)
                prov.deconfigure();
            } catch (CoreException e) {
            	RLogger.logDebug(e,1);
            }
            notifyListeners(Moment.BEFORE, name, pname, db, TreeEvent.TREE_DELETE);
            // db.getStorage().getCacheStorage().remove();//already in db reaction
          }
        } finally {
          // db.setState(TreeState.NOT_LOADED);
          storage.forceNow = false;
        }
      } finally {
        RevisionInfo ri = tsm.getRevisionInfo(db);
        if (db != null) {
          TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
          unbindBaseFromProject(project, db, deleted, null);
        }
        disconnectStorage(project);
        unRegisterNames(project, name); // TODO needed?

        if (db != null) {
          db.setDisposed(true);
          //if (tsm.getState(name, ri) != TreeState.RESTART)
          tsm.setState(name, pname, db, ri, TreeState.HALTED);
        }
        // if(deleted) {
        if (cs != null)
          cs.dispose();
        // }
        if (db != null) {
          if (!deleted) {
            notifyListeners(Moment.AFTER, name, pname, db, TreeEvent.TREE_CLOSE);
          } else {
            notifyListeners(Moment.AFTER, name, pname, db, TreeEvent.TREE_DELETE);
            // db.getStorage().getCacheStorage().remove();//already in db reaction

          }
        }
        uriCache.remove(project); // tends not to be excess for here
        if (pname != null)
          setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
      }
      if (db != null)
        Global.fireClearingEvent(db, null, uri); // clear gitmanager for closed one
      RLogger.logDebug("after stopTracking: " + lname + " " + getTreeStateManager().getState(lname, null), 3);
      RLogger.logDebug("after stopTracking: " + lname + " " + getProjectLoadingState(uri), 3);

      return true;
    } finally {
      isClosing.remove(project);
    }
  }

  private void disconnectStorage(IProject project) {
    ITreeStorage ts = projectToStorage.remove(project);
    if (ts != null) {
      CacheStorage cs = ts.getCacheStorage();
      if (cs != null)
        cs.clearCached();
    }
  }
  @Override
  public void unbindBaseFromProject(Object stored, TreeDB db, boolean delete, File ch) {
    if(!(stored instanceof IProject))
      throw new RuntimeException ("Incorrect stored object given");
    IProject project = (IProject) stored;
    TreeDB dba = treeByProject.remove(project);
    if (dba != null)
      dba.setDisposed(true); // TODO test it
    // projectByTree.remove(db);
    // projectByTree.remove();
    dbnm.forget(db);
    unRegisterNames(project, null);
  }

  @Override
  public void bindTreeToProject(Object proj, TreeDB db, RevisionInfo ri, String lname, File ch) {
    if (!(proj instanceof IProject))
      return;
    IProject project = (IProject) proj;
    db.registerMetaObject(META_ECLIPSE_PROJECT, project);
    TreeDB old = treeByProject.put(project, db);
    if (old != null && db != old) {
      old.setDisposed(true); // TODO return clear cache?
    }
  }

  public boolean isTracking(IProject project) {
    boolean result = treeByProject.containsKey(project);
    if (!result)
      return false;
    TreeDB db = treeByProject.get(project);
    if (db == null || db.isDisposed())
      return false;
    return true;
  }

  public TreeDB[] getTrees() {
    return treeByProject.values().toArray(new TreeDB[0]);
  }

  public IProject[] getProjects() {
    LinkedList<IProject> prjs = new LinkedList<>(treeByProject.keySet());
    prjs.remove(null);
    return prjs.toArray(new IProject[0]);
  }

  public static TreeNode getNodeByResource(DBNamesManager dbnm, IFile file) {
    return getNodeByResource(dbnm, file, false);
  }

  public static TreeNode getNodeByResource(DBNamesManager dbnm, IFile file, boolean ignoreLock) {
    TreeDB db = null;
    try {
      IResTracker rt = getTracker(dbnm);
      String name = rt.getLocalName(file.getParent().getLocation().lastSegment());
      db = rt.getTreeDB(file.getProject(), null, name);
    } catch (Exception e) {
      RLogger.logError(e);
      return null;
    }
    if (db == null)
      return null;
    ResourceStorage rs = getResourceStorage(db);
    UUID uuid = rs.getUUId(file, false);
    if (uuid == null) {
      String name = file.getParent().getName();
      if (name.endsWith(ResManager.RESOURCES_SUFFIX)) {
        uuid = rs.getUUId(file.getParent().getParent()
            .getFile(Path.fromOSString(name.substring(0, name.length() - 10) + ".json")));
      }
      if (uuid == null)
        return null;
    }
    try {
      return db.getNode(uuid);
    } catch (NodeNotFoundException re) {
      // node not found
      return null;
    }
  }

  public static IFile getResourceByNode(TreeNode node, LockType lt) throws LockIsBusy {
    // TODO check it
    if (node == null)
      return null;
    TreeDB db = node.getTreeDB();
    if (db.isDisposed()) {
      String name = db.getLocalName();
      RLogger.logDebug("Tries to get resource from disposed tree " + (name == null ? "NULL" : name)
          + CoreUtils.getShortTrace(), 7);
      return null;
    }
    UUID uid = node.getUUId();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    TreeState ts = tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db));

    if (ts == TreeState.RESTART || ts == TreeState.DISPOSED || ts == TreeState.HALTED || ts == TreeState.NOT_LOADED)
      return null; // calls on not loaded tree
    // if(node.isVirtual())
    // return null;
    ResourceStorage rs = getResourceStorage(db);
    if (rs != null) {
      return rs.getResource(uid, lt);
    }
    return null;
  }

  public static ResourceStorage getResourceStorage(TreeDB db) {
    if (db == null)
      return null;
    ResourceStorage rs = null;
    ITreeStorage ts = db.getStorage();
    if (ts instanceof TransactionStorage) {
      if (((TransactionStorage) ts).getStorage() instanceof ResourceStorage)
        rs = (ResourceStorage) ((TransactionStorage) ts).getStorage();
    } else if (ts instanceof ResourceStorage) {
      rs = (ResourceStorage) ts;
    } else if (ts instanceof TreeInstancer) {
      TreeDB rdb = Instantiator.getRootTree(db, TreeDB.DEFAULT_DEPTH);
      // ITreeStorage ts = null;
      if (rdb != db)
        ts = getResourceStorage(rdb);
      if (ts instanceof ResourceStorage)
        rs = (ResourceStorage) ts;
    }
    return rs;
  }

  public static IResource getMarkerResourceByNode(TreeNode node, LockType lt) throws LockIsBusy {
    if (node.getType().equals(Document.TYPE_NAME)) {
      // TODO think about transaction storage
      ResourceStorage rs = getResourceStorage(node.getTreeDB());
      if (rs != null) {
        String resname = ((Document) node).getResourceName();
        if (resname != null) {
          IFolder rf = rs.getResourceFolder(node.getUUId());
          if (rf != null)
            return rf.getFile(resname);
        }
      }
    }
    return getResourceByNode(node, lt);
  }

  @Override
  public TreeDB[] getTreeDBs() {
    return getTrees();
  }

  private IProject extractProject(Object obj) {
    if (obj instanceof IProject)
      return (IProject) obj;
    if (obj instanceof URI) {
      try {
        return getProject((URI) obj);
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return null;
  }

  private TreeState[] states = new TreeState[] { TreeState.READY, TreeState.CANCELED };

  @Override
  public OperationResult sourceSpecificTrackTreeDB(Object obj, String lname, String pname, boolean ignoreVersion,
      boolean noParallel, boolean check, boolean initCache, TreeDB db) {
    if (db == null)
      throw new RuntimeException("Tries to track null treeDB instance of " + lname);
    if (db.isBaseDisposed())
      throw new RuntimeException("Tries to track disposed treeDB instance of " + lname);
    IProject p = extractProject(obj); // TODO checl
    if (p == null || p.getLocationURI() == null) {
      RLogger.logDebug("Provided object cannot be used to derive project on " + lname);
      return OperationResult.UNSUCEEDED;
    }

    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    RevisionInfo ri = tsm.getRevisionInfo(db);
    TreeState ts = tsm.getState(lname, ri);
    if (ts != TreeState.NOT_LOADED && ts != TreeState.HALTED && ts != TreeState.CANCELED && ts != TreeState.DISPOSED
        && ts != TreeState.RESTART)
      return OperationResult.UNSUCEEDED;
    db.addTreeStateHooks(lname, pname);
    if (pname == null)
      pname = getProjectName(p);
    URI uri = getUri(p, false); // here url is already needs to be loaded (or project is in incorrect state)
//    boolean wasNotLoading = getProjectLoadingState(uri, pname)==ProjectLoadingState.NOT_LOADING;
    final String fname = pname;
    // if(wasNotLoading) {
    // setProjectLoadingState(uri, pname, ProjectLoadingState.INITIAL_LOADING);
    // }
    ResourceStorage rs = (ResourceStorage) db.getStorage();
    projectToStorage.put(p, rs);
    bindTreeToProject(p, db, ri, lname, null);
    dbnm.register(db, DBLocalName.formLocalNameKey(db, lname, db.revision), pname);
    registerProjectNames(p);
    registerNames(p);

    IPath loc = getLocation(p);
    if (uri == null)
      return OperationResult.UNSUCEEDED; // TODO clear?
    OperationResult retRes = OperationResult.UNSUCEEDED;
    com.unitesk.requality.core.operation.IProgressMonitor mon = db.getMonitor(); // TODO IDE monitor
    // updateVersion can reopen treedb
    try {
      try {
        db.getStorage().initCaches(db.getStorage(), CacheInitializationStage.BEFORE_TRACK, mon);
      } catch (Exception e1) {
        RLogger.logError(e1);
      }
      // try {
      // db = getTreeDB(p, ri, lname);
      // } catch (IOException e) {
      // e.printStackTrace();
      // }
      // tsm.setState(lname, ri, TreeState.READY_NO_CACHE);
      // addStateHook(db);
      // .getOne(db.getTracker()).setPartiallyReady(lname, ri);
      ResourcesPlugin.getWorkspace()
          .addResourceChangeListener(((ResourceStorage) db.getStorage()).getResourceChangeListener());
      final TreeDB fdb = db;
      Runnable r = new Runnable() {
        @Override
        public void run() {
          CheckerContainer cc = fdb.getCheckerContainer(ri);
          cc.attachGuiListener(fdb);
          if (check)
            cc.runCheckers();
          WorkspaceJob runBuilder = new WorkspaceJob("Check Requality Project: " + getProjectName(p)) {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
              try {
                p.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
              } catch (CoreException e) {
                RLogger.logError(e);
              }
              return Status.OK_STATUS;
            }
          };
          runBuilder.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
          if (check)
            runBuilder.schedule();
        }
      };
      if (initCache) {
        final TreeDB fdb2 = db;
        tsm.addStateHook(lname, states, new IStateChangeHandler() {
          @Override
            public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
                TreeState oldState, TreeState state) {
            tsm.removeStateHook(lname, states, this);
            if(state == TreeState.READY)
              r.run();
          }

          @Override
          public boolean filterTreeDB(TreeDB db) {
            return db == fdb2;
          }
        });
        db.initCaches(lname, pname, ri, noParallel);
        retRes = noParallel ? OperationResult.SUCEEDED : OperationResult.PARALLEL;
      } else {
        r.run();
        retRes = OperationResult.SUCEEDED;
      }
    } finally {
      // if(!wasIgnored)
      // hook.stateChanges(db, lname, ri, TreeState.READY, TreeState.READY);
    }
    // }else
    // tsm.removeStateHook(lname, tsa, hook);
    return retRes;
  }

  private IPath getLocation(IProject p) {
    IPath pa = p.getRawLocation();
    if (pa != null)
      return pa;
    return p.getLocation();
  }

  byte disabledLis = 0;

  @Override
  public TreeDB createProject(String lname, String pname) {
    TreeState[] states = new TreeState[] { TreeState.READY, TreeState.HALTED, TreeState.CANCELED };
    boolean ended = false;
    IStateChangeHandler ch = null;
    TreeStateManager tsm = TreeStateManager.getOne(this);
    TreeDB old = getTreeDB(null, lname, pname, false, true, monitor); // TODO more proper monitor
    if (old != null)
      throw new RuntimeException("Project with stored name " + pname + " already exist");
    TreeState ts = tsm.getState(lname, null);
    if (!TreeState.isProjectLoadable(ts))
      throw new RuntimeException("Project with name " + lname + " already loading or in incorrect state " + ts);
    boolean rlb = false;
    TreeDB db = null;
    try {
      final IWorkspace workspace = ResourcesPlugin.getWorkspace();
      final IProject project = workspace.getRoot().getProject(lname);
      URI nh = URI.create(workspace.getRoot().getLocationURI() + "/" + CoreUtils.URLEncode(lname) + "/");
      setProjectLoadingState(nh, pname, ProjectLoadingState.START_TRACKING);
      // addNewHandling(nh,lname,pname, true);//to be sure no one more handles it at a
      // time
      dbnm.register(DBLocalName.formLocalNameKey(lname, null), pname);
      tsm.setStateWithoutDB(lname, pname, (RevisionInfo) null, TreeState.NOT_LOADED); // TODO change?
      rl.lock();
      rlb = true;
      RevisionInfo ri = null;
      try {
        if (!project.exists())
          try {
            project.create(null);
          } catch (CoreException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        if (!project.isOpen())
          project.open(null);
        AtomicBoolean isHookRemoved = new AtomicBoolean(false);
        final URI uri = getUri(project, true);
        ch = new AAllTreeStateChangeHandler() { // TODO single?
          private void removeIfNeeded(TreeStateManager ftsm, String lname, TreeState[] fstates,
              IStateChangeHandler sch) {
            synchronized (isHookRemoved) {
              if (!isHookRemoved.get()) {
                isHookRemoved.set(true);
                ftsm.removeStateHook(lname, fstates, sch);
              }
            }
          }

          @Override
          public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
              TreeState oldState, TreeState state) {
            IStateChangeHandler sch = this;
            if (state != TreeState.READY) {
              if (state == TreeState.CANCELED) {
                // TODO can be caused by wrong project?
                setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
                removeIfNeeded(tsm, lname, states, sch);
              }
            } else if (state == TreeState.READY) {
              try {
                project.touch(null);
              } catch (CoreException e) {
                RLogger.logError(e);
              }
              if (IResTracker.storedVersionCorrect(uri)) {
                setProjectLoadingState(uri, pname, ProjectLoadingState.LOADED);
                removeIfNeeded(tsm, lname, states, sch);
              } else {
                RLogger.logDebug("Project " + lname
                    + " loading was initiated but hook found that loading is still not over");
              }
            }
          }
        };
        tsm.addStateHook(lname, states, ch);
        markCacheFolder(project);
        registerProjectNames(project);
        registerNames(project);
        db = TreeDB.createTreeInstance(lname, pname, this, new ResourceStorage(project), null, OFlag.NONE);
        db = runCreate(project, lname, pname, db);
        if (db != null) {
          db.getCheckerContainer().attachGuiListener(db);
          ended = true;
          // removeNewHandling(project);
        }
        // db = EclipseTracker.getTracker().getTree(project);//TODO look into
        ri = TreeStateManager.getRevisionInfo(db);
        notifyListeners(Moment.AFTER, lname, null, db, TreeEvent.TREE_OPEN); // Update state on
        return db;
      } finally {
      }
    } catch (Exception r) {
      r.printStackTrace();
      return null;
    } finally {
      if (!ended)
        tsm.removeStateHook(lname, states, ch);
      if (rlb)
        rl.unlock();
    }
  }

  private TreeDB runCreate(IProject project, String lname, String pname, TreeDB db) {
    try {
      project.refreshLocal(IResource.DEPTH_INFINITE, null);
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    OperationResult or = trackTreeDB(project, lname, pname, true, true, false, true, db);
    if (or == OperationResult.UNSUCEEDED) {
      return null;
    }
    if (or == OperationResult.PARALLEL) {
      RLogger.logError("Paralllel operaton answer during the creation of project " + lname);
      return null;
    }
    notifyListeners(Moment.BEFORE, lname, null, db, TreeEvent.TREE_OPEN); // Update state on
    notifyListeners(Moment.AFTER, lname, null, db, TreeEvent.TREE_OPEN); // Update state on
    return db;
  }

  @Override
  public void init(Pair<String, Object>... params) {
  }

  @Override
  public String getProjectName(TreeDB db) {
    String lname = getLocalName(db);
    if (lname != null) {
      String pname = dbnm.getProjectName(DBLocalName.formLocalNameKey(db, lname, db.revision));
      if (pname != null)
        return pname;
    }
    IProject p = getProject(db);
    if (p == null)
      return null;
    if (p.getLocation() == null)
      return null;
    return CoreUtils.getProjectName(p.getLocation().toFile());
  }

  public static String getProjectName(IProject p) {
    return getProjectName(p, true);
  }

  public static String getProjectName(IProject p, boolean cached) {
    IProjectDescription desc = null;
    if (p == null)
      return null;
    IPath pa = p.getLocation();
    if (cached) {
      if (pa != null) {
        File f = pa.toFile();
        if (f.exists()) {
          String pname = CoreUtils.getProjectName(f.toPath().toFile());
          if (pname != null)
            return pname;
        }
      }
    }
    try {
      desc = p.getDescription();
    } catch (CoreException e) {
    }
    if (desc != null) {
      String name = desc.getName();
      if (name != null)
        return name;
    }
    return null;
  }

  @Override
  public String getLocalName(TreeDB db) {
    IProject p = getProject(db);
    try {
      if (p != null && p.getLocation() != null)
        return p.getLocation().lastSegment();
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return null;
  }

  @Override
  public String getVisibleName(TreeDB db) {
    return getProjectName(db);
  }

  @Override
  public TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean loadCache,
      com.unitesk.requality.core.operation.IProgressMonitor mon) {
    if (lname == null && pname == null)
      return null;
    if (lname != null) {
      IProject p = lProjByName.get(lname);
      TreeDB tree = getTreeByProject(p);
      TreeStateManager tsm = getTreeStateManager();
      if (!load || tree != null)
        return tree;
      else {
        {
          TreeDB db = loadTreeDBDescription(p, null, lname, mon);
          if (db != null) {
            OperationResult or = trackTreeDB(p, lname, pname, false, false, true, loadCache, db);
            if (or == OperationResult.PARALLEL) {
              RLogger.logDebug("getTreeDB causes version update " + lname);
            } else if (or == OperationResult.SUCEEDED || or == OperationResult.LOAD_AS_IT) {
              try {
                TreeDB dba = getTreeDB(p, null, lname);
                notifyListeners(Moment.BEFORE, lname, null, db, TreeEvent.TREE_OPEN); // Update state on
                notifyListeners(Moment.AFTER, lname, null, db, TreeEvent.TREE_OPEN); // Update state on
                return dba;
              } catch (IOException e) {
                RLogger.logError(e);
              }
            }
          }
        }
      }
    }
    if (pname != null) {
      Set<DBLocalName> lnames = dbnm.getLocalNames(pname);
      for (DBLocalName lname2 : lnames) {
        String lpart = lname2.name;
        IProject p = lProjByName.get(lpart);
        TreeDB tree = getTreeByProject(p);
        if (!load || tree != null)
          return tree;
      }
    }

    return null;
  }

  @Override
  public void remove(String lname, String pname) {
    IProject p = null;
    // TODO test
    TreeDB db = getTreeDB(null, lname, pname, true, false, monitor); // TODO IDE monitor
    TreeStateManager tsm = this.tsm;
    RevisionInfo ri = null;
    if (db != null) {
      tsm = TreeStateManager.getOne(db.getTracker());
      db.setDisposed();
      p = getProject(db);
      ri = TreeStateManager.getRevisionInfo(db);
    }
    tsm.setDisposed(lname, pname, db, ri);
    if (p == null)
      p = lProjByName.get(lname);
    if (p == null)
      for (IProject pa : projectToStorage.keySet())
        if (lname.equals(getLocalName(pa)))
          p = pa;
    if (p != null) {
      stopTracking(p, db, null, lname, pname, true);
      unRegisterNames(p, lname);
      try {
        p.delete(true, true, null); // TODO monitor
      } catch (CoreException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
    }
  }

  private Map<IProject, URI> uriCache = new HashMap<>(); // needs to be cleared?

  @Override
  public String getLocation(Object proj) {
    IProject p = extractProject(proj);
    if (p != null) {
      URI uri = p.getLocationURI();
      if (uri != null)
        return uri.toString();
    }
    return null;
  }

  public String getLocation(IProject p, boolean noCache) {
    if (noCache) {
      URI uri = p.getLocationURI();
      return uri == null ? null : uri.toString();
    }
    URI uri = uriCache.get(p);
    if (uri == null) {
      uri = p.getLocationURI();
      if (uri != null)
        uriCache.put(p, uri);
      else
        uri = uriCache.get(p);
    }
    return uri != null ? uri.toString() : null;
  }

  @Override
  public String getLocation(TreeDB db, boolean noCache) {
    IProject p = getProject(db);
    if (p == null) {
      if (!noCache)
        return db.cachedLocation;
      return null;
    }
    String uri = getLocation(p, noCache);
    return uri;
  }

  public URI getUri(IProject p, boolean withCache) {
    if (!withCache)
      return p.getLocationURI();
    if (p == null)
      return null;
    URI ura = uriCache.get(p);
    if (ura == null) {
      ura = p.getLocationURI();
      uriCache.put(p, ura);
    }
    return ura;
  }

  @Override
  public Map<String, Template> getReportTemplates(TreeDB db, TemplateScope ts) {
    Map<String, Template> ret = ts == TemplateScope.WORKSPACE || ts == TemplateScope.BUILDIN
        || ts == TemplateScope.ALL ? Activator.getDefault().getReportTemplates(ts) : Collections.emptyMap();
    if (ret == null || ret.isEmpty())
      return TemplatesManager.getLocalTemplates(db, ts);
    Map<String, Template> localttempl = TemplatesManager.getLocalTemplates(db, ts);
    if (localttempl != null && localttempl.size() > 0) {
      ret = new HashMap<>(ret);
      ret.putAll(localttempl);
    }
    return ret;
  }

  @Override
  public Map<String, Group> getReportGroups(TreeDB db, TemplateScope ts) {
    Map<String, Group> ret = ts == TemplateScope.WORKSPACE || ts == TemplateScope.ALL || ts == TemplateScope.BUILDIN
        ? Activator.getDefault().getReportGroups(ts)
        : Collections.emptyMap();
    if (ret == null || ret.isEmpty())
      return TemplatesManager.getLocalGroup(db, ts);
    Map<String, Group> pgroups = TemplatesManager.getLocalGroup(db, ts);
    if (pgroups != null && pgroups.size() > 0) {
      ret = new HashMap<>(ret);
      ret.putAll(TemplatesManager.getLocalGroup(db, ts));
    }
    return ret;
  }

  @Override
  public void removeTemplate(File to, TreeDB tree, TemplateScope ts) { // TODO test?
    CoreUtils.delete(to, true, false);
    // TODO return templates update?
  }

  private void recCP(File from, File to) {
    if (!to.exists())
      to.mkdirs();
    for (File f : from.listFiles()) {
      File tou = to.toPath().resolve(f.getName()).toFile();
      if (f.isFile())
        try {
          RequalityCLI.copyfile(f.toURI(), tou.toURI());
        } catch (IOException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        }
      else
        recCP(f, tou);
    }
  }

  @Override
  public File addReportTemplate(File from, TreeDB tree, TemplateScope ts) {
    if (from == null)
      return null;
    try {
      File ft = null;
      if (ts == TemplateScope.WORKSPACE)
        ft = new File(ResManager.getManager().getGlobalCacheLocation()).toPath().resolve(".reqcache")
            .resolve("templates").resolve(from.getName()).toFile().getCanonicalFile();
      if (ts == TemplateScope.PROJECT) {
        ft = TemplatesManager.getRootTemplatesFolderPath(tree).resolve(from.getName()).toFile()
            .getCanonicalFile();
      }
      recCP(from, ft);
      return ft;
    } catch (IOException e) {
      RLogger.logError(e);
      // TODO return templates update?
    }
    return null;
  }

  @Override
  public void clearCache(TreeDB db, String localName, boolean delete) {
    IProject p = db.getMetaObject(META_ECLIPSE_PROJECT);
    if (p != null)
      uriCache.remove(p); // we need it to call here for project share case
    super.clearCache(db, localName, delete);
    if (p != null) {
      // projectByTree.remove(db);
      TreeDB dba = treeByProject.get(p);
      if (dba == db) {
        treeByProject.remove(p);
        projectToStorage.remove(p);
        lProjByName.remove(localName);
      } else
        RLogger.logDebug("Db not removed - another instance " + localName);
      //
      // dba.setDisposed(!delete);
    }
    unRegisterNames(p, localName); // TODO needed?
  }

  private IProject getProject(URI path) throws CoreException {
    IWorkspace w = ResourcesPlugin.getWorkspace();
    if (path == null) {
      RLogger.logDebug("No path provided for getProject call "
          + CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      return null;
    }
    IPath ipath = Path.fromPortableString(path.getPath() + "/.project");
    IProjectDescription pd = null;
    try {
      pd = w.loadProjectDescription(ipath);
    } catch (ResourceException re) {
      if (re.getCause() instanceof FileNotFoundException) {
        return null; // desc file not found
      }
      throw re;
    } finally {
    }
    String patha = path.getPath();
    if (patha.endsWith("/"))
      patha = patha.substring(0, patha.length() - 1);
    int lastSeg = patha.lastIndexOf("/");
    if (lastSeg > -1)
      patha = patha.substring(lastSeg + 1);
    IProject p = lProjByName.get(patha);
    if (p == null || !p.exists()) {
      File pa = ipath.toFile().getParentFile();
      if (pa != null) {
        p = getWorkspaceProject(path, pa.getName()); // local name
        if (p != null && p.exists())
          return p;
        p = getWorkspaceProject(path, pd.getName()); // project name
        if (p == null || !p.exists())
          p = getWorkspaceProject(path, CoreUtils.getProjectName(pa));
        if (p == null || (!p.exists() || !p.getLocationURI().equals(path))) {
          for (IProject p2 : w.getRoot().getProjects())
            if (p2.getLocationURI().equals(path))
              return p2;
        }
      }
    }
    if (p == null || !p.exists())
      return null;
    return p;
  }

  @Override
  public String getLocalName(URI path) {
    IProject p;
    try {
      p = getProject(path);
      if (p != null) {
        IPath pth = p.getLocation();
        if (pth != null)
          return pth.lastSegment();
        return p.getFullPath().lastSegment();
      }
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    return null;
  }

  @Override
  public String getProjectName(URI path) {
    IProject p;
    try {
      p = getProject(path);
      if (p != null) {
        return getProjectName(p);
      }
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    return CoreUtils.getProjectName(new File(path));
  }

  @Override
  public String getLocalName(Object proj) {
    if (proj instanceof URI) {
      try {
        IProject p = getProject((URI) proj);
        return getLocalName(p);
      } catch (CoreException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
    }
    if (proj instanceof IProject) {
      IProject p = (IProject) proj;
      return getLocalName(p);
    }
    return null;
  }

  public static String getLocalName(IProject p) {
    IPath pth = p.getLocation();
    if (pth != null)
      return pth.lastSegment();
    return p.getFullPath().lastSegment();
  }

  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, String name,
      com.unitesk.requality.core.operation.IProgressMonitor mon) throws IOException {
    return loadTreeDBDescription(path, ri, false, false, this, mon);
  }

  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, boolean load, boolean ingnoreStVersion,
      IResTracker tr, com.unitesk.requality.core.operation.IProgressMonitor mon) {
    try {
      if (path == null)
        throw new RuntimeException("Path cannot be null");
      IProject p = getProject(path);
      if (p == null)
        throw new RuntimeException(
            "Cannot locate project from provided path " + (path != null ? path : "empty"));
      if (!p.exists())
        return null;
      boolean nh = false;
      URI uri = getUri(p, true);
      String lpartname = p != null ? getProjectName(p) : "unknown";
      if (!p.isOpen()) {
        if (getProjectLoadingState(uri, lpartname) != ProjectLoadingState.VERSION_UPDATE)
          throw new RuntimeException("Loading of not new project " + lpartname);
        p.open(null);
        nh = true;
      }
      final boolean fnh = nh;
      IPath ipath = Path.fromPortableString(path.getPath() + "/.project");
      if (p != null) {
        if (!p.isOpen()) {
          RLogger.logError("Cannot track closed project " + lpartname);
          return null;
        }
        ProjectLoadingState projectLoadingState = getProjectLoadingState(uri, lpartname);
        if (!isCorrect(p) && projectLoadingState.isLoadable()) // TODO
          // test
          // vars
          throw new RuntimeException("Incorrect project! " + lpartname);
        if (!p.isOpen()) {
          RLogger.logInfo("Project is not opened " + lpartname);
          return null;
        }
        mon.beginTask("Load project names " + lpartname, 1);
        String lname = getLocalName(p);
        String pname = CoreUtils.getProjectName(p.getLocation().toFile());
        String rstring = RevisionInfo.toString(null, ri); // special case, we have
        // no dbs here, so use
        // full revision

        ResourceStorage rs = null;

        rs = new ResourceStorage(p);
        mon.doneTask();
        TreeDB db = null;
        mon.beginTask("Actual project instance loading " + lname, 1);
        try {
          db = TreeDB.loadTreeInstance(lname, pname, ri, tr, rs);
          return db;
        } finally {
          mon.doneTask(); // TODO test
        }
        // initDBLoading(p, ri, db);
        // trackProject(p, ri, ingnoreStVersion);
      } else {
        // TODO implement import
      }
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return null;
  }

  @Override
  public java.nio.file.Path getRootLocation() {
    return ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
  }

  @Override
  public URI getGlobalCacheLocation() {
    DialogSettings ds = new DialogSettings("globCache");
    try {
      ds.load("globCache.ini");
    } catch (FileNotFoundException ffe) {

    } catch (IOException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    if (ds.get("path") != null) {
      File path = new File(ds.get("path"));
      if (path.exists())
        return path.toURI();
    }
    return getRootLocation().toFile().toURI();
  }

  @Override
  public void close(TreeDB db) {
    IProject p = getProject(db);
    if (p != null)
      try {
        p.close(null);
        uriCache.remove(p);
        unRegisterNames(p, null);
      } catch (CoreException e) { // TODO Auto-generated catch block
        RLogger.logError(e);
      }
  }

  @Override
  public Object getStoringEntity(TreeDB treeDB) {
    return getProject(treeDB);
  }

  @Override
  public void refresh(Object storingEnt, int depth) throws Exception {
    if (storingEnt != null) {
      IProject p = null;
      if (storingEnt instanceof IProject)
        p = (IProject) storingEnt;
      else if (storingEnt instanceof File) {
        p = getProject(((File) storingEnt).toURI());
      }
      if (p != null)
        p.refreshLocal(depth, null);
    }
  }

  @Override
  public void refresh(TreeDB project, TreeNode resource, int depth) {
    try {
      if (resource == null) {
        IProject p = getProject(project);
        if (p != null && p.isAccessible())
          p.refreshLocal(depth, null);
      } else {
        IResource r = null;
        try {
          r = getResourceByNode(resource, LockType.NO_LOCK);
        } catch (LockIsBusy e) {
          RLogger.logDebug(CoreUtils.getTrace(e), 7);
        }
        if (r != null && r.isAccessible())
          r.refreshLocal(depth, null);
      }
    } catch (ResourceException re) {
      RLogger.logDebug(CoreUtils.getTrace(re));
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
  }

  public CacheStorage getCPCacheStorage(TreeDB dbfrom) {
    try {
      return getCacheStorage();
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return null;
  }

  public CPLinkResolver getCPLinkResolver(TreeDB dbfrom) {
    return getCMM(dbfrom).getLinkManipulator();
  }

  public void handleProjectMove(URI oldURI, URI newURI, String pname) {
    projectLoadingLock.lock();
    try {
      if (oldURI != null) {
        IProject proj = extractProject(oldURI);
        if (proj != null && pname == null)
          pname = EclipseTracker.getProjectName(proj);
        if (pname != null) {
          ProjectLoadingState pls = getProjectLoadingState(oldURI, pname);
          if (pls != ProjectLoadingState.NOT_LOADING) {
            setProjectLoadingState(oldURI, pname, ProjectLoadingState.NOT_LOADING);
            setProjectLoadingState(newURI, pname, pls);
          }
        }
      }
    } finally {
      projectLoadingLock.unlock();
    }
  }

  private File getFile(IProject p) {
    IPath pa = p.getLocation();
    if (pa != null)
      return pa.toFile();
    return p.getFullPath().toFile();
  }

  private void registerProjectNames(IProject project) {
    IPath p = project.getLocation();
    if (p == null)
      return;
    File pname = p.toFile();
    if (pname != null) {
      String lname = getLocalName(project);
      dbnm.register(DBLocalName.formLocalNameKey(lname, RevisionInfo.toString(null, null)),
          CoreUtils.getProjectName(pname));
    }
  }

  @Override
  public void disableListeners(byte flag) {
    disabledLis = (byte) (disabledLis | flag);
  }

  @Override
  public void enableListeners(byte flag) {
    disabledLis = (byte) (disabledLis & ~flag);
  }

  @Override
  public void open(Object obj) {
    try {
      IProject p = extractProject(obj);
      if (p != null) {
        p.open(null);
        registerNames(p);
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }

  // public boolean getErrorState(IProject p) {
  // return errorState.contains(p);
  // }
  private IProject lookFor(String pn, boolean onlyDB) {
    IProject pa = ResourcesPlugin.getWorkspace().getRoot().getProject(pn);
    if (pa != null && (!onlyDB || treeByProject.get(pa) != null && !treeByProject.get(pa).isDisposed()))
      return pa;
    for (Entry<IProject, TreeDB> en : treeByProject.entrySet())
      if (getLocalName(en.getKey()).equals(pn)
          && (!onlyDB || en.getValue().getMetaObject(META_ECLIPSE_PROJECT) != null))
        return en.getKey();
    return null;
  }

  @Override
  public Object getProjectEntity(String lname, String pname, boolean onlyDB) {
    if (lname != null) {
      IProject p = lookFor(lname, onlyDB);
      if (p != null)
        return p;
      else
        for (IProject pa : projectToStorage.keySet())
          if (getLocalName(pa).equals(lname))
            return pa;
    }
    if (pname != null) {
      Set<DBLocalName> lnms = dbnm.getLocalNames(pname);
      if (lnms != null && lnms.size() == 1) {
        DBLocalName s = lnms.iterator().next();
        String lpart = s.name;
        return lookFor(lpart, onlyDB);
      }
    }
    return null;
  }

  public static IProject getProject(URI loc, String name) {
    IWorkspace w = ResourcesPlugin.getWorkspace();
    IWorkspaceRoot wr = w.getRoot();
    IProject pnew = null;
    for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      if (p.exists() && name.equals(getProjectName(p, false))) {
        // such strange toURL because call upper forms it the same way
        pnew = p;
        break;
      }
    return pnew;
  }

  public void callProjectRenameUpdate(URI oldURI, IProject pold, IProject pnew, TreeDB oldDB, RevisionInfo ri,
      String lname, String oldName, String newName) {
    if (oldDB == null)
      oldDB = treeByProject.get(pold);
    if (oldURI == null)
      oldURI = getUri(pold, false);
    if (oldDB != null && !oldDB.isDisposed())
      stopTracking(pold, oldDB, oldURI, lname, oldName, false); // for eclipse rename already called
    if (oldURI != null) {
      java.nio.file.Path p = CoreUtils.getCacheDir(oldURI);
      if (Files.exists(p))
        CoreUtils.delete(p.toFile(), true, true);
    }
    List<URI> newProjs = new LinkedList<>();
    newProjs.add(pnew.getLocationURI()); // TODO test?
    boolean isDis = isDisabledListening(DISABLED_NEW_PROJECTS);
    try {
      enableListeners(DISABLED_NEW_PROJECTS);
      handleNewProjects(newProjs);
    } finally {
      if (isDis)
        isDisabledListening(DISABLED_NEW_PROJECTS);
    }
    // getTreeStateManager().waitState(lname, ri, states, RENAME_STATE_TIMEOUT);
    // TODO new project?
  }

  public static IProject getWorkspaceProject(URI uri, String oldName) {
    IWorkspace ws = ResourcesPlugin.getWorkspace();
    IWorkspaceRoot wr = ws.getRoot();
    for (IProject p : wr.getProjects()) {
      if (p.getName().equals(oldName))
        return p;
      if (uri != null) {
        if (p.getLocationURI().equals(uri))
          return p;
        File pfile = new File(p.getLocationURI());
        if (pfile.exists() && CoreUtils.getProjectName(pfile).equals(oldName))
          return p;

      }
    }
    return null;
  }

  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage, IResTracker repoManager)
      throws IOException {
    IProject p = extractProject(path);
    if (p != null)
      return getTreeByProject(p);
    return null;
  }

  @Override
  public TreeDB getTreeDB(Object proj, RevisionInfo ri, String name) throws IOException {
    IProject p = extractProject(proj);
    if (p != null)
      return getTreeByProject(p);
    Object o = getProjectEntity(name, null, true);
    if (o != null)
      return getTreeDB(o, ri, name);
    return null;
  }

  @Override
  public String getJSResourcesPath() {
    return Activator.getDefault().getJSToolsPath();
  }

  @Override
  public CheckerContainer getCheckerContainer(TreeDB db, RevisionInfo ri) {
    if (ri == null || ri == RevisionInfo.NO_REV || ri == RevisionInfo.NONE)
      return EclipseCheckerContainer.getOne(db, ri);
    return CheckerContainer.DISABLED_CHECKER_CONTAINER;
  }

  @Override
  public void setGUISettings(boolean noGUI, boolean alwaysOkOnNoGui) {
    this.noGUI = noGUI;
    this.alwaysOK = alwaysOkOnNoGui;
  }

  @Override
  public void registerNames(TreeDB db, String lname, String pname) {
    dbnm.register(db, DBLocalName.formLocalNameKey(db, lname, db.revision), pname);
  }

  @Override
  public Collection<File> getAllResourceProjects() {
    List<File> allProjs = new LinkedList<>();
    for (IProject pr : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      if (!pr.isOpen())
        continue;
      IPath path = pr.getLocation();
      if (path != null) {
        File pf = path.toFile();
        if (pf != null && pf.exists())
          allProjs.add(pf);
      }
    }
    return allProjs;
  }

  @Override
  public Pair<Boolean, TreeDB> handleProjectRename(java.nio.file.Path ppath, TreeDB project, String localName,
      String projectNameOld, String projectNameNew, boolean ignoreMessages,
      com.unitesk.requality.core.operation.IProgressMonitor mon) {
    IProject p = getProject(project);
    RevisionInfo ri = project.revision;
    URI loc = null;
    if (p != null) {
      Set<String> names = new HashSet<>();
      names.add(projectNameOld);
      loc = URI.create(getLocation(project, false));
      if (projectNameNew == null) {
        File ffile = new File(URI.create(getLocation(project, false)));
        CoreUtils.forgetName(ffile);
        projectNameNew = CoreUtils.getProjectName(ffile);
      }
      if (projectNameNew != null && projectNameNew.equals(projectNameOld)) {
        return new Pair<Boolean, TreeDB>(false, project);
      }
      IProgressMonitor monitor = (mon instanceof EclipseProgressMonitor)?
            ((EclipseProgressMonitor)mon).getRaw():new NullProgressMonitor();
      try {
        setProjectLoadingState(loc, projectNameNew, ProjectLoadingState.NOT_LOADING);
        strangeEclipseMoveOperation(p, localName, projectNameOld, projectNameNew, monitor);
        TreeState[] states = new TreeState[] { TreeState.READY, TreeState.CANCELED };
        tsm.waitState(localName, ri, states, RENAME_STATE_TIMEOUT * 5);
        TreeState state = tsm.getState(localName, ri);
        if (state == TreeState.HALTED || state == TreeState.CANCELED) {
          return new Pair<Boolean, TreeDB>(false, project);
        }
      } finally {
      }
    }
    return new Pair<Boolean, TreeDB>(true, null);
  }

  private boolean strangeEclipseMoveOperation(IProject p, String lname, String projectOldName, String projectNameNew,
      IProgressMonitor pm) {
    RenameResourceChange errc = new RenameResourceChange(p.getFullPath(), lname);
    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    URI uri = et.getUri(p, false);
    et.disableListeners((byte) (IResTracker.DISABLED_LIS_EVENTS | IResTracker.DISABLED_NEW_PROJECTS));
    errc.initializeValidationData(pm);

    ProjectRenameChange rrrc = new ProjectRenameChange(p, lname, projectOldName, projectNameNew);
    rrrc.initializeValidationData(pm);

    try {
      if (!p.isOpen())
        p.open(pm);
      p.refreshLocal(IResource.DEPTH_INFINITE, pm);
      if (!p.getName().equals(lname))//if the local name will be changed too
        errc.perform(pm);
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    try {
      IProject pnew = EclipseTracker.getProject(uri, lname);
      rrrc.setProjectNew(pnew);
      try {
        ResourcesPlugin.getWorkspace().getRoot().refreshLocal(1, pm);
        tsm.setState(lname, projectOldName, null, TreeState.NOT_LOADED); // TODO projectNewName
        rrrc.perform(pm);
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    } finally {
      et.enableListeners((byte) (IResTracker.DISABLED_LIS_EVENTS | IResTracker.DISABLED_NEW_PROJECTS));
    }
    return true;
  }

  private Change createRenameRefactoring(URI uri, IProject p, String projectNameOld, String projectNameNew) {
    // Activator act = Activator.getDefault();
    // act.setEnableListener(false);
    // try {
    String id = RenameResourceDescriptor.ID;
    RefactoringContribution contrib = RefactoringCore.getRefactoringContribution(id);
    RenameResourceDescriptor desc = (RenameResourceDescriptor) contrib.createDescriptor();
    desc.setProject(null);
    desc.setResourcePath(p.getFullPath());
    desc.setNewName(projectNameNew);
    RefactoringStatus status = new RefactoringStatus();
    try {
      IWorkspace works = ResourcesPlugin.getWorkspace();
      works.checkpoint(false);
      IProgressMonitor monitor = new NullProgressMonitor();
      // works.prepareOperation(null, monitor);
      // works.beginOperation(true);
      Refactoring refactoring = desc.createRefactoring(status);
      refactoring.checkInitialConditions(monitor);
      refactoring.checkFinalConditions(monitor);
      return refactoring.createChange(monitor);
    } catch (CoreException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    // }finally {
    // act.setEnableListener(true);
    // }
    return null;
  }

  private void recSetIgnoreMessage(Change c, boolean ignoreMessages) {
    if (c instanceof CompositeChange) {
      CompositeChange cc = (CompositeChange) c;
      for (Change ca : cc.getChildren())
        recSetIgnoreMessage(ca, ignoreMessages);
    } else if (c instanceof ProjectRenameChange)
      ((ProjectRenameChange) c).setIgnoreMessages(ignoreMessages);
  }

  private Pair<Boolean, Set<TreeDB>> callRenameRefactoring(Change change, boolean ignoreMessages) {
    try {
      Workspace works = (Workspace) ResourcesPlugin.getWorkspace();
      IProgressMonitor monitor = new NullProgressMonitor();
      recSetIgnoreMessage(change, ignoreMessages);
      change.perform(monitor);
      works.checkpoint(false);
      // works.endOperation(null, false);
      return new Pair<>(true, Collections.emptySet());
    } catch (CoreException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return new Pair<>(false, Collections.emptySet());
  }

  public DBNamesManager getDBNamesManager() {
    return dbnm;
  }

  @Override
  public OperationResult handleNewProject(URI uri, String lname, boolean parallel, boolean ignoreMessages) {
    IProject changedProject;
    try {
      changedProject = getProject(uri);
    } catch (CoreException e1) {
      RLogger.logError(e1);
      return OperationResult.UNSUCEEDED;
    }
    if (changedProject == null)
      return OperationResult.UNSUCEEDED;
    TreeStateManager ftsm = getTreeStateManager();
    String projName = getProjectName(changedProject, false);
    if (uri == null || !changedProject.exists() || !changedProject.isAccessible())
      return OperationResult.UNSUCEEDED;
    String fpname = EclipseTracker.getProjectName(changedProject);
    if (projName == null || !projName.equals(fpname))
      return OperationResult.UNSUCEEDED;
    AtomicBoolean nhAdded = new AtomicBoolean(false);

    try {
      ProjectLoadingState state = tracker.getProjectLoadingState(uri, fpname);
      if (state.isLoadable()) {
        // ftsm.addStateHook(flname, fstates, hook);
        nhAdded.set(true);
        tracker.setProjectLoadingState(uri, fpname, ProjectLoadingState.INITIAL_LOADING);
        // tracker.addProjectLoadingHook(nhAdded, uri, fpname);
        if (!newProjectTrack(ftsm, this, changedProject, monitor)) {
          tracker.setProjectLoadingState(uri, fpname, ProjectLoadingState.NOT_LOADING);
          nhAdded.set(false);
          return OperationResult.UNSUCEEDED;
        }
        return OperationResult.SUCEEDED;
      } else if (state == ProjectLoadingState.LOADED) {
        return OperationResult.LOAD_AS_IT; // TODO test
      } else
        RLogger.logDebug("Tries to load project in incorrect state " + lname + " be " + state);
    } catch (Exception e) {
      RLogger.logError(e);
      if (nhAdded.get())
        tracker.setProjectLoadingState(uri, fpname, ProjectLoadingState.NOT_LOADING);
    }
    return OperationResult.UNSUCEEDED;
  }

  private static boolean newProjectTrack(TreeStateManager tsm, EclipseTracker tt, IProject changedProject,
      com.unitesk.requality.core.operation.IProgressMonitor monitor) throws CoreException, IOException {
    if (!changedProject.isOpen() && changedProject.exists())
      changedProject.open(null);
    if (!changedProject.exists())
      return false;
    String localName = tt.getLocalName(changedProject);
    if (localName == null) {
      RLogger.logError("Cannot load name for " + changedProject);
      return false;
    }
    String pname = EclipseTracker.getProjectName(changedProject);
    TreeState state = tsm.getState(localName, RevisionInfo.NO_REV);
    if (!TreeState.isProjectLoadable(state) && state != TreeState.HALTED) {
      RLogger.logDebug("State of " + localName + "(" + state + ") is not suitable for loading", 5);
      return false;
    }
    if (state == TreeState.HALTED) {
      state = TreeState.NOT_LOADED;
      tsm.setState(localName, pname, null, TreeState.NOT_LOADED);
    }
    boolean isNewH = false;
    if (!changedProject.isOpen())
      return false;
    URI uri = changedProject.getLocationURI();
    if (uri == null)
      return false;
    synchronized (tt) {
      ProjectLoadingState pls = tracker.getProjectLoadingState(uri, pname);
      isNewH = !pls.isLoadable() && pls != ProjectLoadingState.START_TRACKING;
    }
    if (!isNewH)
      return false;
    try {
      synchronized (tt) {
        tracker.setProjectLoadingState(uri, pname, ProjectLoadingState.START_TRACKING);
      }
      // changedProject.open(null);
      monitor.beginTask("Load project " + pname, 1);
      // monitor.subTask("Init project "+changedProject.getName());
      boolean tracked = false;
      boolean ended = false;
      IStateChangeHandler sh = null;
      try {
        AtomicBoolean hookAdded = new AtomicBoolean(false);
        if (changedProject.isAccessible() && (!tt.isTracking(changedProject) || TreeState.isDisposed(state))) {
          String name = tt.getLocalName(changedProject);
          String fcachedname = EclipseTracker.getProjectName(changedProject);
          String fpname = EclipseTracker.getProjectName(changedProject, false); // needs to be non-cached
          String descName = changedProject.getName();
          try{
            descName = changedProject.getDescription().getName();
          }catch(CoreException ce){
          }
          if (fcachedname != null && (!fcachedname.equals(fpname)
              || !fcachedname.equals(descName)))
            return false;
          tsm.setState(localName, fpname, null, RevisionInfo.NO_REV, TreeState.NOT_LOADED, true); // drop
                                                      // state
          // allowing to
          // reload a project
          TreeState[] states = new TreeState[] { TreeState.READY, TreeState.CANCELED };
          if (name != null) {
            // Good loading or loading fail
            sh = new AAllTreeStateChangeHandler() { // tree can be changed after version update
              @Override
              public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
                  TreeState oldState, TreeState state) {
                URI uri = changedProject.getLocationURI();
                boolean isStoredCorrect = StoredFormatVersioning.getOne().storedVersionCorrect(uri);
                if (state != TreeState.READY || isStoredCorrect) {
                  if (hookAdded.get()) {
                    tsm.removeStateHook(name, states, this);
                    hookAdded.set(false);
                  }
                  // TODO check it, strange
                  if (state == TreeState.READY && isStoredCorrect)
                    if (tracker.getProjectLoadingState(uri, pname) != ProjectLoadingState.LOADED)
                      tracker.setProjectLoadingState(uri, pname, ProjectLoadingState.LOADED); // TODO
                  // move
                  // to
                  // single
                  // place
                  if (changedProject.isOpen() && state == TreeState.READY) {
                    WorkspaceJob tch = new WorkspaceJob(
                        "Update derived files for project " + pname) {
                      @Override
                      public IStatus runInWorkspace(IProgressMonitor monitor)
                          throws CoreException {
                        try {
                          if (changedProject.isOpen())
                            changedProject.touch(monitor);
                        } catch (CoreException e) {
                          RLogger.logError(e);
                        }
                        return Status.OK_STATUS;
                      }
                    };
                    tch.schedule();
                  }
                }
              }

              @Override
              public boolean filterTreeDB(TreeDB db) {
                return true; // TODO get instncae?
              }
            };
            hookAdded.set(true);
            tsm.addStateHook(name, states, sh);
          }
          // URI uri = changedProject.getLocationURI();
          String name1 = tt.getLocalName(changedProject);
          TreeDB old = tt.getTreeDB(changedProject, null, name1); // TODO reloading
          TreeState ts = tsm.getState(name1, null);
          TreeDB n = null;
          if ((old == null || (TreeState.isProjectLoadable(ts))) && !tracker.isClosing.contains(changedProject)) {
            if (old != null) {
              old.setDisposed(false);
              tsm.setState(name1, pname, null, TreeState.NOT_LOADED); // update state?
            }
            n = tt.getTreeDB(null, name1, pname, false, monitor);
            if (n != null && !n.isDisposed())
              return false; // already loaded
            n = tt.loadTreeDBDescription(changedProject, null, name1, monitor);
            OperationResult or = tt.trackTreeDB(changedProject, name1, pname, false, true, true, true, n);
            if (or == OperationResult.PARALLEL) {
              ts = tsm.getState(name1, null);
            } else if (or == OperationResult.UNSUCEEDED) {
              ts = tsm.getState(name1, null);
              if (hookAdded.get()) {
                hookAdded.set(false);
                tsm.removeStateHook(name, states, sh);// remove hook, new loading will be hold by updater
              }
              IProject p = tt.extractProject(changedProject); // TODO checl
              if (p != null && p.getLocationURI() != null) {
                CacheStorage.closeRecConnection(new File(p.getLocationURI()));
              }
            } else {
              TreeDB dba = tt.getTreeDB(changedProject, null, name1);
              ts = tsm.getState(name1, null);
              if (dba == n) { // if treeDB instance was not changed
                tracker.notifyListeners(Moment.BEFORE, name1, null, n, TreeEvent.TREE_OPEN);// Update state
                // on
                tracker.notifyListeners(Moment.AFTER, name1, null, n, TreeEvent.TREE_OPEN);// Update state
                // on
              }
            }
          }
        }
      } catch (RuntimeException re) {
        // TODO proper exception
        if (re != null && re.toString().startsWith("Cannot loc")) {
          RLogger.logDebug(CoreUtils.getTrace(re));
          return false;
        } else
          throw re;
      } finally {
        monitor.doneTask();
        // (!ended && sh!=null)
        // sh.stateChanges(null, localName, null, null, null);
      }
    } finally {
    }
    return true;
  }

  private Lock loadingLock = new ReentrantLock();
  private Set<IProject> isClosing = new HashSet<IProject>();
  private IResourceChangeListener resourceChangeListener = new IResourceChangeListener() {
    public int detectChangeKind(int changeKind) {
      return changeKind;
    }

    public int detectDeltaChangeKind(int changeKind, int flags) {
      // close 2 del 4 post_change 1 rem 2 prebuild 8
      if ((flags & IResourceDelta.MOVED_FROM) == IResourceDelta.MOVED_FROM)
        return IResourceChangeEvent.POST_CHANGE;
      if ((flags & IResourceDelta.MOVED_TO) == IResourceDelta.MOVED_TO)
        return IResourceChangeEvent.PRE_DELETE;
      if ((flags & IResourceDelta.OPEN) == IResourceDelta.OPEN)
        return IResourceChangeEvent.POST_CHANGE;
      switch (changeKind) {
      case IResourceDelta.REMOVED:
        return IResourceChangeEvent.PRE_DELETE;
      case IResourceDelta.CHANGED:
        return IResourceChangeEvent.POST_CHANGE;
      case IResourceDelta.ADDED:
        return IResourceChangeEvent.POST_CHANGE;
      }
      return changeKind;
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
      if (isDisabledListening(IResTracker.DISABLED_LIS_EVENTS))
        return;
      int eventKind = detectChangeKind(event.getType());
      boolean projChanged = event.getDelta() != null && event.getDelta().getResource() instanceof IWorkspaceRoot;
      if (projChanged) {
        for (IResourceDelta delt : event.getDelta().getAffectedChildren())
          handleChange(detectDeltaChangeKind(delt.getKind(), delt.getFlags()), delt.getResource());
        return;
      }

      handleChange(eventKind, event.getResource());
    }

    public void handleChange(int type, IResource resource) {
      if (type == IResourceChangeEvent.PRE_CLOSE) {
        EclipseTracker tracker = EclipseTracker.this;
        IProject p = (IProject) resource;
        if (isClosing.contains(p))
          return;
        URI uri = tracker.getUri(p, true); // here we use a cached url
        String pname = uri != null ? CoreUtils.getProjectName(new File(uri)) : null;
        String lname = EclipseTracker.getLocalName(p);
        CoreUtils.lockWorkspace("close Project " + pname);
        isClosing.add(p);
        try {
          stopTracking(p, null, null, lname, pname, false);
          unRegisterNames(p, lname);
        } finally {
          CoreUtils.unlockWorkspace();
        }
        return;
      }
      if (type == IResourceChangeEvent.PRE_DELETE && (resource instanceof IProject)) {
        IProject p = (IProject) resource;
        if (isClosing.contains(p))
          return;
        URI uri = tracker.getUri(p, true); // here we use a cached url
        String pname = uri != null ? CoreUtils.getProjectName(new File(uri)) : null;
        String lname = EclipseTracker.getLocalName(p);
        CoreUtils.lockWorkspace("delete Project " + pname);
        isClosing.add(p);
        try {
          stopTracking(p, null, null, lname, pname, true);
        } finally {
          CoreUtils.unlockWorkspace();
        }
        return;
      }
      if (type == IResourceChangeEvent.POST_CHANGE) {
        if (type == IResourceDelta.REMOVED)
          return;
        // boolean isMove = type == IResourceChangeEvent.MOVED_TO ||
        // type == IResourceChangeEvent.MOVED_FROM;
        // Track new
        final EclipseTracker tracker = Activator.getTracker();
        IProject newProjects[] = getNewProjects();
        TreeStateManager tsm = tracker.getTreeStateManager();
        boolean changed = false;
        if (newProjects != null && newProjects.length > 0) {
          for (IProject p : newProjects) {
            URI uri = p.getLocationURI();
            if (uri == null)
              continue;
            changed = true;
            timeoutToLoad.put(uri, System.currentTimeMillis() + NEW_PROJET_TIMEOUT);
          }
          if (changed) {
            registerNewProjectLoader(timeoutToLoad);
          }
        }
      }
      if (type == IResourceChangeEvent.PRE_BUILD) {
        // Track new
        IProject project = resource.getProject();
        if (project != null) {
          try {
            project.refreshLocal(IProject.DEPTH_INFINITE, null);
          } catch (CoreException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        }
      }
    }
  };

  private void newProjectsActions(Collection<IProject> loading,
      com.unitesk.requality.core.operation.IProgressMonitor monitor) {

    try {
      // a.enLi = false;
      TreeStateManager tsm = tracker.getTreeStateManager();
      final TreeStateManager ftsm = tsm;
      // plist.removeAll(tracker.getNewHandlings());

      RLogger.logDebug("Startrs requality init projects task", 5);
      String name = "Init Requality projects [" + Arrays.toString(loading.toArray()) + "]";
      CoreUtils.lockWorkspace(name);
      monitor.beginTask(name, loading.size());
      // for (IProject changedProject : reqProjects)
      // changedProject.close(null);
      // EclipseTracker tt = tracker;
      // CacheStorage cs = tt.getCPCacheStorage(null);
      // String cp = cs.startCacheTransaction("Init crossproject storage during init
      // ", CacheStorage.COMMIT_ALL);
      try {
        for (IProject changedProject : loading) {
          URI uri = changedProject.getLocationURI();
          String pname = EclipseTracker.getProjectName(changedProject);
          if (pname == null || tracker.getProjectLoadingState(uri, pname) != ProjectLoadingState.NOT_LOADING)
            continue;
          String flname = tracker.getLocalName(changedProject);
          if (!tracker.isClosing.contains(changedProject))
            tracker.handleNewProject(uri, flname, false, false);
        }
      } finally {
        CoreUtils.unlockWorkspace();
        // ResManager.getManager().getTracker().setInited(true);
      }
    } finally {
      // a.enLi=!DEBUG_FILE_TRACKER;
    }
  }

  // private void initLoadingJob() {
  // loadingLock.lock();
  // try {
  // if (loadingJon == null || loadingTaked.get()) {
  // loadingTaked.set(false);
  // loadingJon = new WorkspaceJob("Init Requality projects") {
  // @Override
//          public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
  //
  // }
  // };
  // loadingJon.schedule();
  // }
  // } finally {
  // loadingLock.unlock();
  // }
  // }

  @Override
  public boolean isDisabledListening(byte b) {
    return (disabledLis & b) == b;
  }

  public void addIsClosing(IProject p) {
    isClosing.add(p);
  }

  private IProject[] getNewProjects() {
    Set<IProject> openProjects = new HashSet<>();
    for (IProject proj : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      if (proj.isOpen() && isLoadableRequalityProject(proj) && isCorrect(proj) && !isClosing.contains(proj)
          && proj.getLocationURI() != null) {
        String lname = EclipseTracker.getLocalName(proj);
        TreeState ts = tracker.getTreeStateManager().getState(lname, RevisionInfo.NO_REV);
        if (TreeState.isProjectLoadable(ts))
          openProjects.add(proj);
      }
    }
    return openProjects.toArray(new IProject[openProjects.size()]);
  }

  public static boolean isLoadableRequalityProject(IProject proj) {
    try {
      if (!proj.isOpen()) {
        return false;
      }
      if (!proj.hasNature(Consts.CATALOG_NATURE)) {
        return false;
      }
      if (proj.hasNature(Consts.CATALOG_BROKEN_NATURE))
        return false;
      if (isBroken(proj))
        return false;
    } catch (CoreException e) {
      RLogger.logError(e);
      return false;
    }
    return true;
  }

  public static boolean isBroken(IProject proj) {
    IFile f = proj.getFile("root.json");
    if (!f.exists())
      return true; // broken project
    return false;
  }

  public static boolean isCorrect(IProject changedProject) {
    IFile root = changedProject.getFile("root.json");
    if (!root.exists() || !root.isAccessible())
      return false;
    return true;
  }

  private ConcurrentLinkedDeque<IProject> onUpdateQueue = new ConcurrentLinkedDeque<>();
  private AtomicBoolean newProviderLoaderStopped = new AtomicBoolean(true);

  public void registerNewProjectLoader(Map<URI, Long> newProjs) {
    projectLoadingLock.lock();
    try {
      if (!newProviderLoaderStopped.get())
        return;
      JobManager.jm.startJob("Load projects", new RunnableWithMonitor() {
        @Override
        public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
          synchronized (EclipseTracker.this) {
            if (!newProviderLoaderStopped.get())
              return;
            newProviderLoaderStopped.set(false);
          }
          List<URI> toLoad = null;
          while (!newProjs.isEmpty()) {
            long min = Long.MAX_VALUE;
            boolean minChanged = false;
            for (Entry<URI, Long> en : newProjs.entrySet()) {
              if (en.getValue() < System.currentTimeMillis()) {
                if (toLoad == null)
                  toLoad = new ArrayList<>();
                toLoad.add(en.getKey());
              } else {
                min = Math.min(min, en.getValue());
                minChanged = true;
              }
            }
            if (toLoad != null) {
              for (URI ur : toLoad)
                newProjs.remove(ur);
              handleNewProjects(toLoad);
            }
            long waitTime = min - System.currentTimeMillis();
            if (minChanged && waitTime > 0)
              try {
                Thread.currentThread().join(waitTime);
              } catch (InterruptedException e) {
              }
          }
          synchronized (EclipseTracker.this) {
            newProviderLoaderStopped.set(true);
          }
        }
      }, true);
    } finally {
      projectLoadingLock.unlock();
    }
  }

  @Override
  public void handleNewProjects(List<URI> reqProjects) {
    List<IProject> toLoad = new LinkedList<>();
    for (URI uri : reqProjects) {
      IProject changedProject = null;
      try {
        changedProject = getProject(uri);
      } catch (CoreException e1) {
        RLogger.logError(e1);
      }
      if (changedProject != null && changedProject.isAccessible() && isLoadableRequalityProject(changedProject)) {
        String pname = EclipseTracker.getProjectName(changedProject);
        if (!isCorrect(changedProject))
          RLogger.logError("Project " + pname + " has corrupted structure!");
        else {
          // if (loadingJob != null)
          // loadingEnded.set(true);
          // loadingEnded = new AtomicBoolean(false);
          // final AtomicBoolean ab = loadingEnded;
          synchronized (onUpdateQueue) {
            if (!onUpdateQueue.contains(changedProject)) {
              onUpdateQueue.add(changedProject);
              toLoad.add(changedProject);
            }
          }
        }
      }
    }
    RunnableWithMonitor projsLoadingJob = new RunnableWithMonitor() {
      @Override
      public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
        boolean locked = false;
        while (!locked) {
          try {
            locked = loadingLock.tryLock(500, TimeUnit.MILLISECONDS);
          } catch (InterruptedException e) {
            RLogger.logError(e);
            return;
          }
        }
        try {
          synchronized (loadingLock) {
            if ((disabledLis & IResTracker.DISABLED_NEW_PROJECTS) == IResTracker.DISABLED_NEW_PROJECTS) {
              // tracker.removeNewHandlings(Arrays.asList(newProjects));
              return;
            }
            try {
              newProjectsActions(toLoad, pm);
              return;
            }catch (RuntimeException e) {
              RLogger.logInfo(e);
            } 
            catch (Exception e) {
              RLogger.logError(e);
            }
            return;
          }
        } finally {
          if (locked)
            loadingLock.unlock();
          synchronized (onUpdateQueue) {
            onUpdateQueue.removeAll(toLoad);
          }
        }
      }
    };
    if (toLoad.size() > 0)
      JobManager.jm.startJob("Init loading for projects " + Arrays.toString(toLoad.toArray()),
        projsLoadingJob, true);
  }
}
