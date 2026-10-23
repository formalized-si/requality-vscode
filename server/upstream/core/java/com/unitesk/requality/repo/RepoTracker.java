package com.unitesk.requality.repo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.reference.RelationsTreeContext;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.AbstractResTracker;
import com.unitesk.requality.core.storage.CPLinkResolver;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.FilesTracker;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.TrackerCacheManipulatorsManager;
import com.unitesk.requality.core.storage.TreeEvent;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.tool.CoreUtils;

/***
 * Storage for different repo versions of trees
 * 
 * @author kildishev
 */

public class RepoTracker extends AbstractResTracker {
  public static final String REPO_TRACKER_FOLDER = "repoTracker";
  private IResTracker baseTracker;
  private FilesTracker ft = new FilesTracker(new DBNamesManager());
  private static RepoTracker gtr;
  private CacheStorageManager csm = null;;
  private Path cpPath = null;
  private DBSlice sliceNow = DBSlice.EMPTY;
  private DBSlice sliceHead = null;
  //private Map<String, DBSlice> sliceHeads = new HashMap<>();

  public DBSlice getHeadSlice(RepoManager rm, TreeDB db, IProgressMonitor mon) throws Exception {
    synchronized (this) {
      String dbname = db.getLocalName();
      DBSlice headSlice = sliceHead;
      mon.beginTask("Slice init - check cache", 1);
      // TODO sync?
      if (headSlice == null) {
        mon.doneTask();
        mon.beginTask("Slice init - not in cache, loading", 1);
        try {
          RelationsTreeContext related = CoreUtils.gatherRelatedTrees(db, null, -1, null, true);
          RevisionInfo ri = rm.getHeadRevision();
          
          HashMap<TreeDB, RevisionInfo> riMap = new HashMap<>();
          for(TreeDB dba:related.allRelated) {
            RepoManager rma = Global.getRepoManager(dba);
            if(rma!=null)
              riMap.put(dba, rma.getHeadRevision());
          }
          RevisionInfo headRev = new SliceRevisonInfo(ri, riMap);
          
          headSlice = DBSlice.create(getCacheStorageManager(), related.allRelated.toArray(new TreeDB[0]), // TODO
                                                          // more
                                                          // proper
                                                          // use
              rm, db, headRev, mon);
        } finally {
          mon.doneTask();
        }
      } else {
        mon.doneTask();
      }
      return headSlice;
    }
  }

  public static RepoTracker getRepoTracker(IResTracker tr) {
    if (tr instanceof RepoTracker)
      return (RepoTracker) tr;
    synchronized (RepoTracker.class) {
      if (gtr == null) {
        gtr = new RepoTracker(tr);
        tr.addListener(Moment.AFTER, new ITreeStateListener() {

          @Override
          public void settingsChanged(TreeDB db) {
            // TODO Auto-generated method stub

          }

          @Override
          public void open(TreeDB db) {
            // TODO Auto-generated method stub

          }

          @Override
          public void delete(String lname, String pname, TreeDB db) {
            close(db);
          }

          @Override
          public void close(TreeDB db) {
            gtr.sliceNow = null;
            if(gtr.sliceHead!=null)
              gtr.sliceHead.delete();
            gtr.sliceHead = null;
          }
        });
      }
      return gtr;
    }
  }

  private Thread hook = new Thread(new Runnable() {
    @Override
    public void run() {
      deleteMarkedSlices();
    }
  });

  public RepoTracker(IResTracker rt) {
    super();
    setGUISettings(true, true);
    baseTracker = rt;
    Path path = CoreUtils.getCacheDir(baseTracker.getGlobalCacheLocation()).resolve(REPO_TRACKER_FOLDER);
    if(!Files.exists(path))
      try {
        Files.createDirectory(path);
       } catch (IOException e) {
       }
    ft.init(new Pair<String, Object>(ResManager.PATH,
        path.toUri().toString()),
        new Pair<String, Object>(ResManager.TEMPL_PATH, null));
    ft.setGUISettings(true, true);
    tsm = TreeStateManager.getOne(ft);
    cpPath = CoreUtils.getCacheDir(getGlobalCacheLocation());
    csm = new CacheStorageManager(cpPath);
    deleteMarkedSlices();
    Runtime.getRuntime().addShutdownHook(hook);
    // baseTracker.setGUISettings(true,true);
  }

  @Override
  public CacheStorage getCacheStorage(String id) {
    return ft.getCacheStorage(id);
  }

  @Override
  public void remove(String lname, String pname) {
    throw new RuntimeException("not implemented");
  }

  @Override
  public String getLocation(Object proj) {
    return ft.getLocation(proj);
  }

  @Override
  public String getLocation(TreeDB treeDB, boolean noCache) {
    return ft.getLocation(treeDB, noCache);
  }

  @Override
  public Map<String, Template> getReportTemplates(TreeDB db, TemplateScope ts) {
    return baseTracker.getReportTemplates(db, ts);
  }

  @Override
  public void removeTemplate(File to, TreeDB db, TemplateScope ts) {
    throw new RuntimeException("not implemented");
  }

  @Override
  public File addReportTemplate(File from, TreeDB db, TemplateScope ts) {
    throw new RuntimeException("not implemented");
  }

  @Override
  public Map<String, Group> getReportGroups(TreeDB db, TemplateScope ts) {
    return baseTracker.getReportGroups(db, ts);
  }

  @Override
  public void clearCache(TreeDB p, String localName, boolean del) {
    resetSlice(CoreUtils.getMonitorOrConsole(p));
    csm.clear(p, del);
    if(gtr.sliceHead!=null)
      gtr.sliceHead.delete();
    gtr.sliceHead = null;
    ft.clearCache(p, localName, del);
  }

  @Override
  public OperationResult sourceSpecificTrackTreeDB(Object o, String lname, String pname, boolean ignoreVersion, boolean noParallel,
      boolean check, boolean initCache, TreeDB db) {
//  String loc = getLocation(db);
    // if(loc != null)
    return ft.sourceSpecificTrackTreeDB(o, lname, pname, ignoreVersion, noParallel, check, initCache, db);
    // return trackTreeDB(o, URI.create(loc), tsm.getRevisionInfo(db), true, false,
    // this);
    // else
    // throw new RuntimeException("not implemented");
  }

  @Override
  public void initClasses(TreeDB db) {
    ft.initClasses(db);
  }

  @Override
  public URI getGlobalCacheLocation() {
    // TODO test
    return ft.getGlobalCacheLocation();
//    RevisionInfo ri = baseTree.getRevisionInfo();
//    String as = ri!=null?ri.getDate().toString():"";
    // return CoreUtils.getCacheDir(baseTree).resolve(".gc"+as).toUri().toString();
  }

  private TreeStateManager tsm = null;

  @Override
  public void close(TreeDB db) {
    tsm.setDisposed(db.getLocalName(), db.getProjectName(), db, tsm.getRevisionInfo(db));
    ft.close(db);
  }

  @Override
  public void refresh(TreeDB project, TreeNode resource, int depth) {
    project.getTracker().refresh(project, resource, depth);
  }

  @Override
  public Object getStoringEntity(TreeDB treeDB) {
    if (treeDB.getTracker() == this)
      return ft.getStoringEntity(treeDB);
    return treeDB.getTracker().getStoringEntity(treeDB);
  }

  @Override
  public void refresh(Object storingEnt, int depth) throws Exception {
    baseTracker.refresh(storingEnt, depth);// TODO mb bug
  }

  @Override
  public boolean addListener(Moment m, ITreeStateListener l) {
    // TODO ignore?
    return ft.addListener(m, l);
    // throw new RuntimeException("not implemented");
  }

  @Override
  public boolean removeListener(Moment m, ITreeStateListener l) {
    // TODO ignore?
    return ft.removeListener(m, l);

  }

  @Override
  public synchronized void notifyListeners(Moment m, String lname, String pname, TreeDB db, TreeEvent type) {
    ft.notifyListeners(m, lname, pname, db, type);
  }

  @Override
  public synchronized TrackerCacheManipulatorsManager getCMM(TreeDB dbfrom) {
    String si = getSliceId(dbfrom);
    if (!si.isEmpty())
      return csm.getCMM(this, si);
    return ft.getCMM(dbfrom);
  }

  public DBSlice getSlice() {
    return sliceNow;
  }

  public void setSlice(DBSlice s, IProgressMonitor mon) throws Exception {
    if (sliceNow != s) {
      if (sliceNow != null)
        sliceNow.disable(mon);
      sliceNow = s;
      if (sliceNow != null)
        sliceNow.enable(mon);
    }
  }

  private Stack<DBSlice> slicesToDelete = new Stack<>();

  public void markSliceToDelete(DBSlice s) {
    if (s == DBSlice.EMPTY)
      return;
    slicesToDelete.add(s);
  }

  public void deleteMarkedSlices() {
    while (!slicesToDelete.isEmpty()) {
      DBSlice oldSlice = slicesToDelete.pop();
      oldSlice.delete();
    }
    slicesToDelete.clear();
  }

  @Override
  public TreeStateManager getTreeStateManager() {
    return ft == null ? null : ft.getTreeStateManager();
  }

  @Override
  public CacheStorage getCPCacheStorage(TreeDB db) {
    String si = getSliceId(db);
    if (!si.isEmpty())
      try {
        return csm.getCacheStorage(si);
      } catch (Exception e) {
        RLogger.logError(e);
      }
    return ft.getCPCacheStorage(db);
  }

  @Override
  public CPLinkResolver getCPLinkResolver(TreeDB dbfrom) {
    String si = getSliceId(dbfrom);
    if (si != null && !si.isEmpty())
      return csm.getLinkResolver(this, si);
    return ft.getCPLinkResolver(dbfrom);
  }

  private String getSliceId(TreeDB db) {
    DBSlice dbs = TreeStateManager.getSliceInfo(db);
    String sliceId = null;
    if (dbs != null && dbs != DBSlice.EMPTY)
      sliceId = dbs.getSliceId();
    else
      sliceId = sliceNow != null && sliceNow != DBSlice.EMPTY ? sliceNow.getSliceId() : "";
    if (sliceId == null)
      return "";
    return sliceId != null ? sliceId : "";// null id can be get when slice is still initing
  }

  @Override
  public TreeDB[] getTreeDBs() {
    return ft.getTreeDBs();
    // throw new RuntimeException("not implemented");
  }

  @Override
  public TreeDB createProject(String lname, String pname) {
    throw new RuntimeException("create baseTree is not supported for this res tracker");
  }

  @Override
  public void init(Pair<String, Object>... params) {
    // baseTracker.init(params);
    // TODO mb required
  }

  @Override
  public String getLocalName(TreeDB db) {
    return ft.getLocalName(db);
  }

  @Override
  public String getVisibleName(TreeDB db) {
    return ft.getVisibleName(db);
  }

  @Override
  public String getProjectName(TreeDB db) {
    return ft.getProjectName(db);
  }

  @Override
  public TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean loadCache,
      IProgressMonitor mon) {
    // boolean isRemote = baseTree.getTracker() instanceof RepoTreeTracker;
    DBSlice dbs = TreeStateManager.getSliceInfo(dbFrom);
    if (dbs != null && dbs != DBSlice.EMPTY && dbs.isEnabled())
      return dbs.innerGetTreeDB(dbFrom, lname, pname, load, loadCache);
    return ft.getTreeDB(dbFrom, lname, pname, load, loadCache, mon);
  }

  @Override
  public Path getRootLocation() {
    // TODO Auto-generated method stub
    return ft.getRootLocation();
  }

  public IResTracker getBase() {
    return baseTracker;
  }

  public FilesTracker getRevisionsTracker() {
    return ft;
  }

  public void resetSlice(IProgressMonitor mon) {
    try {
      setSlice(DBSlice.EMPTY, mon);
    } catch (Exception e) {// TODO Auto-generated catch block
      RLogger.logError(e);
    }
  }

  @Override
  public void disableListeners(byte b) {
    ft.disableListeners(b);
  }

  @Override
  public void enableListeners(byte b) {
    ft.enableListeners(b);
  }
  @Override
  public boolean isDisabledListening(byte b) {
    return ft.isDisabledListening(b);
  }

  @Override
  public void open(Object obj) {
    ft.open(obj);
  }
  @Override
  public ProjectLoadingState getProjectLoadingState(URI uri, String pname) {
    return ft.getProjectLoadingState(uri, pname);
  }
  @Override
  public ProjectLoadingState setProjectLoadingState(URI uri, String pname, ProjectLoadingState state) {
    return ft.setProjectLoadingState(uri, pname, state);
  }

  @Override
  public Object getProjectEntity(String lname, String pname, boolean onlyDB) {
    return ft.getProjectEntity(lname, null, true);
  }

  @Override
  public String getJSResourcesPath() {
    return baseTracker.getJSResourcesPath();
  }

  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage,
      IResTracker repoManager, IProgressMonitor mon) throws IOException {
    return ft.loadTreeDBDescription(path, ri, ignorev, ignorestorage, repoManager, mon);
  }

  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage, IResTracker repoManager)
      throws IOException {
    return ft.getTreeDB(path, ri, ignorev, ignorestorage, repoManager);
  }

  @Override
  public String getLocalName(URI path) {
    return ft.getLocalName(path);
  }

  @Override
  public String getProjectName(URI path) {
    return ft.getProjectName(path);
  }

  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, String name, IProgressMonitor mon)
      throws IOException {
    return ft.loadTreeDBDescription(path, ri, name, mon);
  }

  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, String name) throws IOException {
    return ft.getTreeDB(path, ri, name);
  }

  @Override
  public TreeDB loadTreeDBDescription(Object project, RevisionInfo ri, String name, IProgressMonitor mon)
      throws IOException {
    return ft.loadTreeDBDescription(project, ri, name, mon);
  }

  @Override
  public TreeDB getTreeDB(Object proj, RevisionInfo ri, String name) throws IOException {
    return ft.getTreeDB(proj, ri, name);
  }

  @Override
  public void initDBLoading(Object proj, RevisionInfo ri, TreeDB db) {
    ft.initDBLoading(proj, ri, db);
  }

  @Override
  public String getLocalName(Object proj) {
    return ft.getLocalName(proj);
  }

  @Override
  public boolean isOpen(Object project) {
    return ft.isOpen(project);
  }

  @Override
  public void close(Object project) {
    ft.close(project);
  }

  @Override
  public CheckerContainer getCheckerContainer(TreeDB newDB, RevisionInfo ri) {
    return ft.getCheckerContainer(newDB, ri);
  }

  @Override
  public void markNonLoadable(URI uri) {
    ft.markNonLoadable(uri);
  }

  @Override
  public void setGUISettings(boolean noGUI, boolean alwaysOkOnNoGui) {
    ft.setGUISettings(noGUI, alwaysOkOnNoGui);
  }

  public CacheStorageManager getCacheStorageManager() {
    return csm;
  }

  public DBSlice createTempSlice(Set<TreeDB> allRelatedTreeDBs, RepoManager rm, TreeDB old, RevisionInfo ri,
      IProgressMonitor mon) throws Exception {
    DBSlice slice = DBSlice.create(getCacheStorageManager(), allRelatedTreeDBs.toArray(new TreeDB[0]), rm, old, ri,
        mon);
    markSliceToDelete(slice);
    return slice;
  }

  @Override
  public void bindTreeToProject(Object proj, TreeDB db, RevisionInfo ri, String lname, File ch) {
    ft.bindTreeToProject(proj, db, ri, lname, ch);
  }

  @Override
  public void registerNames(TreeDB db, String lname, String pname) {
    ft.registerNames(db, lname, pname);
  }

  @Override
  public Collection<File> getAllResourceProjects() {
    return ft.getAllResourceProjects();
  }

  @Override
  public Pair<Boolean, TreeDB> handleProjectRename(Path ppath, TreeDB project, String localName,
      String projectNameOld, String projectNameNew, boolean ignoreMessages, IProgressMonitor mon) {
    return ft.handleProjectRename(ppath, project, localName, projectNameOld, projectNameNew, ignoreMessages, mon);
  }

  @Override
  public ReentrantReadWriteLock getProjectLoadingLock() {
    return ft.getProjectLoadingLock();
  }

  @Override
  public OperationResult handleNewProject(URI loc, String lname, boolean parallel, boolean ignoreMessages) {
    return ft.handleNewProject(loc, lname, parallel, ignoreMessages);
  }

  @Override
  public void handleNewProjects(List<URI> reqProjects) {
    ft.handleNewProjects(reqProjects);
  }
}
