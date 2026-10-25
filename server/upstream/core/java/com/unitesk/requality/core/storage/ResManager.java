package com.unitesk.requality.core.storage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.create.CreateNode;
import com.unitesk.requality.core.secure.SecureDataManager;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.RestrictedFolder;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;

public class ResManager {
  public static final String ATTR_RES_VAR =  "{node.resURL}";
  public static final String RES_VAR = "{node.resNodeURL}";
  public static final String RESOURCES_SUFFIX = "_resources";
//Static tracker to be inited for new resource manager instance
  public static String PATH = "pathToStorage", TEMPL_PATH="pathToTemplates", ALLOWED_PROJECTS = "allowedProjectsNames", FORCE_RAW_NAMES = "forceRawNames",
      DROP_CACHE_ON_INIT = "dropCacheOnInit";
  //private static IResTracker gtt;
  //Singleton exemplar
  private static ResManager tm = null;
  @SuppressWarnings("cast")
  private static Pair<String, Object>[] lastParams = (Pair<String, Object>[])new Pair[0];
  //Tracker of single instance
  private IResTracker tt;
  /**
   * * Default constructor. May have tracker-specific init params.
   *
   * @see FilesTracker
   * @param params tracker-specific init params
   */
  private ResManager(Pair<String, Object>... params) {
    if (params.length > 0) 
      lastParams = params;
    else 
      params = lastParams;
    tt = rmp.initTracker(params);
    if(API.GENERAL_SALT == null) {
      Path cache = CoreUtils.getCacheDir(getGlobalCacheLocation());
      if(cache!=null) {
        File f = cache.resolve(".pepper").toFile();
        if(!f.getParentFile().exists())
          f.getParentFile().mkdirs();
        byte[]fc = null;
        if(f.exists())
  	  	try {
  	  		fc = Files.readAllBytes(f.toPath());
  	  	} catch (IOException e) {
  	  		// TODO Auto-generated catch block
  			e.printStackTrace();
  		}
        if(fc == null) {
          fc = SecureDataManager.generateSalt(256).get();
          try {
  	  	  Files.write(f.toPath(), fc, StandardOpenOption.CREATE);
          } catch (IOException e) {
  			e.printStackTrace();
          }
        }
        API.GENERAL_SALT = fc;
      }
    }
  }
  /** * Get list of available TreeDBs from active tracker. */
  public TreeDB[] getTreeDBs() {
    return tt.getTreeDBs();
  }
  private static ResManagerProvider rmp = FilesTracker.PROVIDER;
  public static void changeResManagerProvider(ResManagerProvider rmp2) {
    if(rmp!=rmp2) {
      rmp = rmp2;
      if(tm!=null)
        tm = null;
    }
  }
  
  private static boolean isSame(Pair<String, Object>[] paramsOld, Pair<String, Object>[] paramsNew) {
    if(paramsNew.length == 0)
      return true;//if we have non-init calls with empty params then ignore them
    if(paramsOld.length!=paramsNew.length)
      return false;
    for(int i = 0;i<paramsNew.length;i++) {
      Pair<String, Object> pold = paramsOld[i];
      Pair<String, Object> pnew = paramsNew[i];
      if(!pold.getFirst().equals(pnew.getFirst()))
        return false;
      if(pold.getSecond()==null && pnew.getSecond() == null || pold.getSecond()!=null && pold.getSecond().equals(pnew.getSecond()))
        return false;
    }
    return true;
  }
  public static void setParamsOnly(Pair<String, Object>... params) {
    lastParams = params;
  }
  /**
   * * Method to init instance of Resource Manager. If forceFiles is enabled then the instance will
   * be initiated with Files Tracker instance. Otherwise it will use available tracker. In a case
   * eclipse is active it will use Resource Tracker.
   *
   * @param forceFiles if true then ResManager will use Files Tracker instead of Eclipse Tracker
   * @param params tracker-specific init params
   * @return Resource Manager instance with available tracker.
   */
  public static ResManager getManager(Pair<String, Object>... params) {
    boolean ch = false;
    if (tm == null || !isSame(lastParams, params)) {
      tm = new ResManager(params);
      ch = true;
    }
   // if (ch) tm.tt.init(params);

    if(API.GENERAL_SALT == null) {
    	File f = new File(tm.tt.getGlobalCacheLocation()).toPath().resolve(CoreUtils.CACHE_FOLDER_NAME).resolve(".pepper").toFile();
    	byte[]fc = null;
    	if(f.exists())
			try {
				fc = Files.readAllBytes(f.toPath());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	if(fc == null) {
    	  fc = SecureDataManager.generateSalt(256).get();
    	  try {
			Files.write(f.toPath(), fc, StandardOpenOption.CREATE);
          } catch (IOException e) {
			e.printStackTrace();
		  }
        }
    	API.GENERAL_SALT = fc;
    }
    return tm;
  }
  /**
   * * Check that given TreeDB is tracking by active tracker.
   *
   * @param db TreeDB to be checked
   * @return true if db is tracked
   */
  public boolean isTracking(TreeDB db) {
    if(db == null)
      return false;
    for (TreeDB db2 : tt.getTreeDBs()) if (db.equals(db2)) return true;
    return false;
  }
  /**
   * * Get name of given TreeDB from .project. Names will be taken from trees tracker and in most cases are equal
   * to name of db-containing folder.
   *
   * @param db TreeDB for which name will be given
   * @return name of TreeDB if it is tracked, null in other cases
   */
  public String getProjectName(TreeDB db) {
    if (db == null) return null;
    return tt.getProjectName(db);
  }
  /**
   * * Get local name of given TreeDB from workspace. Names will be taken from trees tracker and in most cases are equal
   * to name of db-containing folder.
   *
   * @param db TreeDB for which name will be given
   * @return name of TreeDB if it is tracked, null in other cases
   */
  public String getLocalName(TreeDB db) {
    if (db == null) return null;
    return tt.getLocalName(db);
  }
  /**
   * * Get visible name of given TreeDB from workspace. In transaction mode shows name of transaction, else - name from getProjectName()
   *
   * @param db TreeDB for which name will be given
   * @return name of TreeDB if it is tracked, null in other cases
   */
  public String getVisibleName(TreeDB db) {
    if (db == null) return null;
    return tt.getVisibleName(db);
  }
  /**
   * * Get TreeDB by name if have one with given name in trees tracker.
   *
   * @param name name of TreeDB to be searched
   * @return TreeDB instance if tracker have one with given name, null in other cases
   */
  public TreeDB getTreeDB(TreeDB dbfrom,String localName, String projectName, boolean load, IProgressMonitor mon) {
    try {
      return tt.getTreeDB(dbfrom, localName, projectName, load, mon);
    }catch(Exception r) {
      RLogger.logDebug(r,7);
    }
    return null;
  }
  /**
   * * Remove TreeDB with given name if have one tracked.
   *
   * @param name name of TreeDB to be deleted
   */
  public void remove(String lname, String pname) {
    tt.remove(lname, pname);
  }
  /**
   * * Returns string representation of path to TreeDB instance if it is tracked.
   *
   * @param treeDB TreeDB to get path
   * @return path to TreeDB if it is tracked
   */
  public URI getLocation(TreeDB treeDB) {
    return treeDB.getStorage().getLocation();
  }
  //TODO to be replaceable in tests
  public static void createProjectStructure(TreeDB db) {
    //tt.disableNewListeners();
    try{
      try{
        TreeNode node = db.getRootNode();
        if (node.findWorkChild("Requirements") == null
            || node.findWorkChild("Types") == null
            || node.findWorkChild("Documents") == null
            || node.findWorkChild("Reports") == null
            || node.findWorkChild("Instances") == null) {
          {
            if (node.findWorkChild("Requirements") == null) {
              CreateNode.call(node, Requirement.TYPE_NAME,"Requirements");
            }
            if (node.findWorkChild("Types") == null) {
              CreateNode.call(node, NodeType.TYPE_NAME,"Types");
            }
            if (node.findWorkChild("Documents") == null) {
              CreateNode.call(node, DocFolder.TYPE_NAME,"Documents");
            }
            if (node.findWorkChild("Reports") == null) {
              CreateNode.call(node, ReportFolder.TYPE_NAME,"Reports");
            }
            if (node.findWorkChild("Templates") == null) {
              CreateNode.call(node,RestrictedFolder.TYPE_NAME, "Templates");
            }
          }
          //Fixes pre-inited variables for non-inited case
          node.getTreeDB().updateNodeChildren(node);
        }
      }finally{
      }
    }
    finally{
      //tt.enableNewListeners();
    }
  }
//  /**
//   * * Method to close and open TreeDB. Now used for compatibility with eclipse project close-open
//   * mechanics and corresponding Requality operations like storage version conversion
//   *
//   * @param treeDB
//   * @return new instance of TreeDB
//   */
//  public TreeDB reOpen(TreeDB treeDB, boolean del, boolean ignoreMessages) {
//    return tt.reOpen(treeDB, del, ignoreMessages);
//  }
//
//  public static void resetOne() {
//    tm = null;
//  }

  public IResTracker getTracker() {
    return tt;
  }
  public java.nio.file.Path getRootLocation() {
    return tt.getRootLocation();
  }
  public URI getGlobalCacheLocation() {
    return tt.getGlobalCacheLocation();
  }
  public void close(TreeDB db){// TODO Auto-generated method stub
	  tt.close(db);
	  DBNamesManager.DEFAULT.forget(db);
  }
  public void refresh(TreeDB project, int depth) {
    // TODO Auto-generated method stub
    tt.refresh(project, null, depth);
  }
  public void refresh(Object project, int depth) throws Exception{
    // TODO Auto-generated method stub
    tt.refresh(project, depth);
  }
  public void refresh(TreeDB project, TreeNode resource, int depth) {
    // TODO Auto-generated method stub
    tt.refresh(project, resource, depth);
  }
  public void removeTemplate(TreeDB db, File to, TemplateScope ts) {
    try{
      tt.removeTemplate(to, db, ts);
    }finally{
      if(ts == TemplateScope.WORKSPACE || ts == TemplateScope.BUILDIN)
        TemplatesManager.getOne(null).clearCaches();  
      TemplatesManager.getOne(db).clearCaches();
      TemplatesManager.getOne(db).getLabelOrder();
    }
  }
  public File addReportTemplate(TreeDB db, File from, TemplateScope ts) {
	File f2 = from.toPath().resolve("template.xml").toFile();
	if(!f2.exists())
      throw new RuntimeException("Target folder "+from.toPath().toString()+" is not a template folder - no template.xml file was found");
	String id = TemplatesManager.getTemplateIdForFolder(from);
	if(id == null)
	  throw new RuntimeException("Cannot load template info for folder "+from.toPath().toString());
	Template t = TemplatesManager.getOne(db).getTemplate(id);
	if(t!=null)
      throw new RuntimeException("Template "+id+" already exist in template set");
    try{
      return tt.addReportTemplate(from, db, ts);
    }finally{
      if(ts == TemplateScope.WORKSPACE || ts == TemplateScope.BUILDIN)
        TemplatesManager.getOne(null).clearCaches();  
      TemplatesManager.getOne(db).clearCaches();
      TemplatesManager.getOne(db).getLabelOrder();
    }
  }
  public RepoManager getRepoManager(TreeDB db) {
    return Global.getRepoManager(db);
  }
  /**
   * * Get report templates from tracker. TODO move it to API
   *
   * @return report templates in form of map id->template
   */
  public Map<String, Template> getTemplates(TreeDB db, TemplateScope ts) {
    return tt.getReportTemplates(db, ts);
  }

  public Map<String, Group> getGroups(TreeDB db, TemplateScope ts) {
    return tt.getReportGroups(db, ts);
  }
  public void addListener(Moment m, ITreeStateListener tcl) {
    tt.addListener(m, tcl);
  }
  public void removeListener(Moment m, ITreeStateListener tcl) {
    tt.removeListener(m, tcl);
  }
  public String getJSResourcesPath() {
	return tt.getJSResourcesPath();
  }
  public Collection<File> getAllResourceProjects() {
    return tt.getAllResourceProjects();
  }
  public static final Object RENAME_PROJECT_SYNC = "Rename project sync object";
  public static boolean ignoreWorkspaceOperationInRename = false;
  public Pair<Boolean, TreeDB> handleProjectRename(Path ppath, TreeDB project, String localName, String projectNameOld, String projectNameNew, boolean ignoreMessages, IProgressMonitor monitor) {
    boolean old = false;
    synchronized (RENAME_PROJECT_SYNC) {
      old = ignoreWorkspaceOperationInRename;
      ignoreWorkspaceOperationInRename = true;
    }
    try {
      if(projectNameNew==null || projectNameOld!=null && !projectNameOld.equals(projectNameNew))
        return tt.handleProjectRename(ppath, project, localName, projectNameOld, projectNameNew, ignoreMessages, monitor);
      return new Pair<>(false, project);
    }finally {
      synchronized (RENAME_PROJECT_SYNC) {
        ignoreWorkspaceOperationInRename = old;
	  }
    }
  }
  private static Set<Integer> deletingHashes = new HashSet<>();
  public static boolean isMarkedAsDeleting(TreeDB project) {
    if(project == null)
      return false;
    return deletingHashes.contains(getHash(project));
  }
  private static String ID_NAME = "IND_NUMBER";
  private static int ID = 0;
  private static int getHash(TreeDB project) {
    Integer storedId = project.getMetaObject(ID_NAME);
    if(storedId == null) {
      synchronized (ID_NAME) {
        storedId = project.getMetaObject(ID_NAME);
        if(storedId == null) {
          storedId = ID++;
          project.registerMetaObject(ID_NAME, storedId);
        }
      }
    }
    return storedId;
  }
  public static void markTreeAsDeleting(TreeDB db) {
    if(db == null)
      return;
    deletingHashes.add(getHash(db));
  }
}
