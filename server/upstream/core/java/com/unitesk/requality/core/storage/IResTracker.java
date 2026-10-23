package com.unitesk.requality.core.storage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.ICacheStorageProvider;
import com.unitesk.requality.core.storage.formatversion.StoredFormatVersioning;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;

public interface IResTracker extends ICacheStorageProvider {
  public static boolean storedVersionCorrect(URI uri) {
    return StoredFormatVersioning.getOne().storedVersionCorrect(uri);
  }
  IResTracker NULL = new IResTracker() {
    @Override
    public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, String name, IProgressMonitor mon)
        throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void removeTemplate(File to, TreeDB tree, TemplateScope ts) {
      // TODO Auto-generated method stub

    }

    @Override
    public boolean removeListener(Moment m, ITreeStateListener l) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public void remove(String lname, String pname) {
      // TODO Auto-generated method stub

    }

    @Override
    public void refresh(TreeDB project, TreeNode resource, int depth) {
      // TODO Auto-generated method stub

    }

    @Override
    public void notifyListeners(Moment m, String lname, String pname, TreeDB db, TreeEvent type) {
      // TODO Auto-generated method stub

    }

    @Override
    public void initClasses(TreeDB db) {
      // TODO Auto-generated method stub

    }

    @Override
    public void init(Pair<String, Object>... params) {
      // TODO Auto-generated method stub

    }

    @Override
    public TreeDB[] getTreeDBs() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean loadCache,
        IProgressMonitor mon) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Path getRootLocation() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Map<String, Template> getReportTemplates(TreeDB db, TemplateScope ts) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getLocation(Object proj) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getLocation(TreeDB treeDB, boolean noCache) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public URI getGlobalCacheLocation() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CPLinkResolver getCPLinkResolver(TreeDB dbfrom) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CacheStorage getCPCacheStorage(TreeDB dbfrom) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CacheStorage getCacheStorage(String stateModelName) {
      return null;
    }

    @Override
    public TreeDB createProject(String lname, String pname) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void close(TreeDB db) {
      // TODO Auto-generated method stub

    }

    @Override
    public void clearCache(TreeDB p, String localName, boolean delete) {
      // TODO?
    }

    @Override
    public File addReportTemplate(File from, TreeDB tree, TemplateScope ps) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean addListener(Moment m, ITreeStateListener l) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, boolean load, boolean ignoreVers,
        IResTracker repoManager, IProgressMonitor mon) throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void disableListeners(byte flag) {
      // TODO Auto-generated method stub

    }

    @Override
    public void enableListeners(byte flag) {
      // TODO Auto-generated method stub

    }

    @Override
    public void open(Object obj) {
      // TODO Auto-generated method stub

    }

    @Override
    public CacheStorage getCacheStorage(TreeDB dbfrom) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public TrackerCacheManipulatorsManager getCMM(TreeDB dbfrom) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void clearNodeCache(TreeDB p, TreeNode node, boolean delete) {
      // TODO Auto-generated method stub

    }

    @Override
    public Object getStoringEntity(TreeDB treeDB) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void refresh(Object storingEnt, int depth) {
      // TODO Auto-generated method stub

    }

    @Override
    public Map<String, Group> getReportGroups(TreeDB db, TemplateScope ts) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getProjectName(TreeDB db) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getLocalName(TreeDB db) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getVisibleName(TreeDB db) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Object getProjectEntity(String lname, String pname, boolean onlyDB) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getJSResourcesPath() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public TreeDB getTreeDB(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage,
        IResTracker repoManager) throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public TreeDB getTreeDB(URI path, RevisionInfo ri, String name) throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getLocalName(URI path) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getLocalName(Object proj) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public OperationResult trackTreeDB(Object proj, String lname, String pname, boolean ignoreVersion,
        boolean noParallel, boolean check, boolean initCache, TreeDB db) {
      // TODO Auto-generated method stub
      return OperationResult.UNSUCEEDED;
    }

    @Override
    public void initDBLoading(Object proj, RevisionInfo ri, TreeDB db) {
      // TODO Auto-generated method stub

    }

    @Override
    public CheckerContainer getCheckerContainer(TreeDB newDB, RevisionInfo ri) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public TreeDB loadTreeDBDescription(Object project, RevisionInfo ri, String name, IProgressMonitor mon)
        throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public TreeDB getTreeDB(Object proj, RevisionInfo ri, String name) throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean isOpen(Object project) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public void close(Object project) {
      // TODO Auto-generated method stub

    }

    @Override
    public void setGUISettings(boolean noGUI, boolean alwaysOkOnNoGui) {
      // TODO Auto-generated method stub

    }

    @Override
    public TreeStateManager getTreeStateManager() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean isInited() {
      // TODO Auto-generated method stub
      return true;
    }

    @Override
    public void setInited(boolean b) {
      // TODO Auto-generated method stub

    }

    @Override
    public TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, IProgressMonitor mon) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Collection<File> getAllResourceProjects() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Pair<Boolean, TreeDB> handleProjectRename(Path ppath, TreeDB project, String localName,
        String projectNameOld, String projectNameNew, boolean ignoreMessages, IProgressMonitor monitor) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ReentrantReadWriteLock getProjectLoadingLock() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ProjectLoadingState getProjectLoadingState(URI uri, String pname) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ProjectLoadingState setProjectLoadingState(URI uri, String pname, ProjectLoadingState state) {
      return ProjectLoadingState.NOT_LOADING;
    }

    @Override
    public OperationResult handleNewProject(URI loc, String lname, boolean parallel, boolean ignoreMessages) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void handleNewProjects(List<URI> reqProjects) {
    }

    @Override
    public boolean isDisabledListening(byte b) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public String getProjectName(URI path) {
      // TODO Auto-generated method stub
      return null;
    }

  };
  byte DISABLED_LIS_EVENTS = 1;
  byte DISABLED_NEW_PROJECTS = 2;

  TreeDB[] getTreeDBs();

  TreeDB createProject(String lname, String pname);

  void init(Pair<String, Object>... params);

  String getProjectName(TreeDB db);

  String getProjectName(URI path);

  String getLocalName(TreeDB db);

  String getVisibleName(TreeDB db);

  TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, IProgressMonitor mon);

  TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean loadCache, IProgressMonitor mon);

  TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, boolean load, boolean ignorestorage,
      IResTracker repoManager, IProgressMonitor mon) throws IOException;

  TreeDB getTreeDB(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage, IResTracker repoManager)
      throws IOException;

  String getLocalName(URI path);

  TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, String name, IProgressMonitor mon) throws IOException;

  TreeDB getTreeDB(URI path, RevisionInfo ri, String name) throws IOException;

  TreeDB loadTreeDBDescription(Object project, RevisionInfo ri, String name, IProgressMonitor mon) throws IOException;

  TreeDB getTreeDB(Object proj, RevisionInfo ri, String name) throws IOException;

  void initDBLoading(Object proj, RevisionInfo ri, TreeDB db);

  String getLocalName(Object proj);

  Object getProjectEntity(String lname, String pname, boolean onlyDB);

  OperationResult trackTreeDB(Object proj, String lname, String pname, boolean ignoreVersion, boolean noparallel,
      boolean check, boolean initCache, TreeDB db);

  void remove(String lname, String pname);

  String getLocation(TreeDB treeDB, boolean noCache);

  String getLocation(Object proj);

  Map<String, Template> getReportTemplates(TreeDB db, TemplateScope ts);

  void removeTemplate(File to, TreeDB tree, TemplateScope ts);

  File addReportTemplate(File from, TreeDB tree, TemplateScope ts);

  Map<String, Group> getReportGroups(TreeDB db, TemplateScope ts);

  void clearCache(TreeDB p, String localName, boolean delete);

  void clearNodeCache(TreeDB p, TreeNode node, boolean delete);

  void initClasses(TreeDB db);

  Path getRootLocation();

  URI getGlobalCacheLocation();

  boolean isOpen(Object project);

  void close(Object project);

  void close(TreeDB db);

  void open(Object o);

  Object getStoringEntity(TreeDB treeDB);

  void refresh(Object storingEnt, int depth) throws Exception;

  void refresh(TreeDB project, TreeNode resource, int depth);

  boolean addListener(Moment m, ITreeStateListener l);

  boolean removeListener(Moment m, ITreeStateListener l);

  void notifyListeners(Moment m, String lname, String pname, TreeDB db, TreeEvent type);

  CacheStorage getCacheStorage(String stateModelName);

  CacheStorage getCPCacheStorage(TreeDB dbfrom);

  CPLinkResolver getCPLinkResolver(TreeDB dbfrom);

  void disableListeners(byte flag);

  void enableListeners(byte flag);

  boolean isDisabledListening(byte b);

  TrackerCacheManipulatorsManager getCMM(TreeDB dbfrom);

  String getJSResourcesPath();

  CheckerContainer getCheckerContainer(TreeDB newDB, RevisionInfo ri);

  void setGUISettings(boolean noGUI, boolean alwaysOkOnNoGui);

  TreeStateManager getTreeStateManager();

  boolean isInited();

  void setInited(boolean b);

  ProjectLoadingState getProjectLoadingState(URI uri, String pname);

  ProjectLoadingState setProjectLoadingState(URI uri, String pname, ProjectLoadingState state);

  Collection<File> getAllResourceProjects();

  Pair<Boolean, TreeDB> handleProjectRename(Path ppath, TreeDB project, String localName, String projectNameOld,
      String projectNameNew, boolean ignoreMessages, IProgressMonitor monitor);

  ReentrantReadWriteLock getProjectLoadingLock();

  OperationResult handleNewProject(URI loc, String lname, boolean parallel, boolean ignoreMessages);

  void handleNewProjects(List<URI> reqProjects);

}
