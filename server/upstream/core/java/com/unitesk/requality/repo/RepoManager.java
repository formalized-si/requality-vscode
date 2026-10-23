package com.unitesk.requality.repo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IAPICacheManDBProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.AbstractResTracker;
import com.unitesk.requality.core.storage.CPLinkResolver;
import com.unitesk.requality.core.storage.FSStorage;
import com.unitesk.requality.core.storage.FileSpaceTimePoint;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.LinkResolver;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.TrackerCacheManipulatorsManager;
import com.unitesk.requality.core.storage.TreeEvent;
import com.unitesk.requality.core.storage.cache.CacheManipulator;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.IgnoredManipulator;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class RepoManager extends AbstractResTracker 
 implements ITreeChangeListener, IAPICacheManDBProvider 
  {
  /** Константы */
  public static final int CVCLMANAGER_OK = 0;
  //private TreeStateManager tsm = null;
  private static CacheStorage globalRevisionedCS = null;
  private static LinkResolver globalRevisionedLR = null;

  public static final String[] CVCLMNGR_ERROR_MESSAGES = {
    "OK",
    "Failed to create/open .cvcl folder",
    "Failed to create/open history folder",
    "Failed to create/open temp folder",
    "Failed to create/open temp file",
    "The required feature is not implemented (either support for team provider in use is not built in CVCL manager or repository client is not comprehended)",
    "File seems to be not within the given repository (repository/file URLs mismatch, the log might help)",
    "Failed to create/open folder for package history",
    "Some error has happened (the log might help)",
    "Failed to create/open component history file",
    "There is no team provider assigned to the project, CVCL manager cannot work without it.",
    "Repository interface has failed: either initialization problems, or reading history problem, or getting file problems",
    "Repository authentication problems",
    "The demanded package has not been found in aadl program. The reason might be the fact, that this package is new and has not been commited to the repository yet. Please, commit the local copy of the file.",
    "The CVCL was not able to get revision of the file. Either there is problem with connection to the repository or file is not in the repository yet. Try to roll in file to the repostory first.",
    "The local revision of the file is different from the revision in the repository. Please, update your local copy and then try again.",
    "CVCL has not found any changes in the component, so there is nothing to commit.",
    "Component seems to be absent in the repository version of the file. Nothing to revert.",
    "CVCL has not found any changes in the component, so there is nothing to revert."
  };
  public static final String REPO_NODE_VERSION = "_repoNodeVersion";
  public static final String REPO_NODE_ALL_VERSIONS = "_repoNodeAllVersions";
  public static final String REPO_TREE_VERSION = "_repoTreeVersion";
  public static final String REPO_TREE_PAGED_VERSION = "_repoTreePagedVersion";
  public static final String REPO_NODE_PAGED_VERSION = "_repoNodePagedVersion";
  public static final String IGNORE_CACHE_MAN = "IgnoreCacheMan";

  /** Поля */
  // Поля, связанные с клиентом SVC
  //private ICVSClient svcClient = null; // SVC клиент

  // Поля, связанные с проектом
  private File workingPlace =
      null; // папка внутри рабочей папки проекта, где хранятся вспомогательные папки и файлы
  private String projectAbsolutePath = null; // Абсолютный путь до рабочей папки проекта
  //private String workspaceAbsolutePath = null;

  private File historyFolder = null; // папка с файлами историй компонентов
  private File tempFolder = null; // папка для временных файлов

  private int lastError = CVCLMANAGER_OK; // Код последней ошибки
  private HashMap<String, String> errorsInfo = new HashMap<>(); // Полная информация об ошибках
  private TreeDB db = null;
  private RepoTracker rt = null;
  private Set<UUID> bupDel = new HashSet<>();
  // Другие поля
  //private UsernamePasswordDialog usrPasswdDialog = null;

  /** Методы */
  public RepoManager() {
    noGUI = true;
    alwaysOK = true;
  }
  
  public RepoManager(TreeDB db) {
    this();
    //super(); //TODO no state changes?
    this.db = Instantiator.getRootTree(db);
    rt = RepoTracker.getRepoTracker(this.db.getTracker());
    setGUISettings(true, true);
  }

  public HashMap<String, String> getErrorsInfo() {
    return errorsInfo;
  }

  private void gatherErrorMsg(String msg, Exception e) {
    if (msg == null) {
      msg = e.getClass().getSimpleName();
    }
    if (e != null) {
      StringBuilder st = new StringBuilder();
      for (StackTraceElement err : e.getStackTrace()) {
        st.append(err);
        st.append(System.getProperty("line.separator"));
      }
      this.errorsInfo.put(msg, st.toString());
    } else {
      this.errorsInfo.put(msg, "");
    }
  }  
  public Pair<RevisionInfo, TreeDB> getRevisionTree(String revisionInfo, boolean forcespecific, boolean dropCaches, IProgressMonitor mon) {
    ICVSClient man = null;
    try {
      man = getCVSClient();
    } catch (Exception e) {
      RLogger.logDebug(CoreUtils.getTrace(e));
      gatherErrorMsg(e.getLocalizedMessage(), e);
    }
    RevisionInfo ri = null;
    try{
      if (man != null) {
        ri = man.getRevisionInfo(revisionInfo, mon);
        if(ri!=null)
          return getRevisionTree(ri, true, forcespecific, dropCaches, true, mon);
      }
    } catch (Exception e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
    }
    return null;
  }
//  private Map<String, TreeDB> map = new HashMap<>();//TODO move to proper loc
  private Pair<RevisionInfo, TreeDB> getRevisionTree(RevisionInfo revisionInfo, boolean initCache, boolean waitLoaded, IProgressMonitor mon) {
    return getRevisionTree(revisionInfo, true, initCache, waitLoaded, mon);
  }
  public void forgetRevisionTree(RevisionInfo ri){
    if(ri == null)
      return;
    String exc = null;
    try {
      String key = null;
      String keyAlt = null;
      ICVSClient man = getCVSClient();
      if (man != null) {
        boolean freed = false;
        try{
          freed = man.freeRevision(ri.getRevision());
          key = ri.getRevision();
          keyAlt = ri.getDate().toString();
        }catch(Exception e){
          if(!e.getMessage().startsWith("Missing unknown "))
            exc = CoreUtils.getTrace(e);
        }
        if(!freed){
          ri = man.getProjectRevisionByDate(ri.getDate());
          if(ri!=null && ri != RevisionInfo.NONE){
//            map.remove(ri.getDate().toString());
//            map.remove(ri.getRevision());
          }
        }
        if(freed){
          exc = null;//another loading way
//          TreeDB db = map.remove(key);
//          map.remove(keyAlt);
          rt.getRevisionsTracker().stopTracking(db, ri, false);
        }
      }
    } catch( com.unitesk.requality.repo.CVSException cvs){
      cvs.printStackTrace();
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      RLogger.logError(e);
    }finally {
      if(ri!=null) {
//        map.remove(ri.getRevision());
//        map.remove(ri.getStringDate());
      }
    }
    if(exc!=null)
      RLogger.logError(exc);
  }
  public Pair<RevisionInfo, TreeDB> getRevisionTree(RevisionInfo revisionInfo, boolean load, boolean initCache, boolean waitLoaded, IProgressMonitor mon) {
    return getRevisionTree(revisionInfo, load, false, initCache, waitLoaded, mon);
  }
  public Pair<RevisionInfo, TreeDB> getRevisionTree(RevisionInfo revisionInfo, boolean load, boolean forceSpecific, boolean initCache, boolean waitLoaded, IProgressMonitor mon) {
    if(revisionInfo == null || revisionInfo == RevisionInfo.NONE || revisionInfo.getRevision(db).equals("NONE"))
      return null;
    String lname = db == null?null:db.getLocalName();
    DBSlice slice = rt.getSlice();
    if(slice!=DBSlice.EMPTY && !forceSpecific)
    {
       mon.beginTask("load existing treeDB from slice "+db.getSemiLocalName(), 1);
       try {
        TreeDB dba = slice.getStoredTree(db.getLocalName());
        RevisionInfo ri = slice.getMapSliceRevs().get(db.getLocalName());
        if(dba!=null && !dba.isDisposed())
          return new Pair<>(ri, dba);
        if(ri!=null) {
          if(revisionInfo instanceof SliceRevisonInfo) {
            SliceRevisonInfo sri = (SliceRevisonInfo) revisionInfo;
            if(sri.getRevision(db)==null)//if no rev in slice rev
              revisionInfo = ri;
          }
        }
       }finally {
         mon.doneTask();
       }
    }
    TreeDB tree = null;
    String exc = null;
    String rev = revisionInfo.getRevision();//slice rev id
//    tree = map.get(rev);
//    if(tree!=null && !tree.isDisposed())
//      return new Pair<>(revisionInfo, tree);
    if(!load)
      return new Pair<>(revisionInfo, null);
    try {
      String key = null, ok = rev;;
      ICVSClient man = getCVSClient();
      if (man != null) {
        Pair<File, Boolean> pc = null;
        try{
          pc = man.getProjectContentByRevision(this, revisionInfo,  revisionInfo.getRevision(), revisionInfo.getRevision(db), waitLoaded, mon);
          key = ok;
        }catch(Exception e){
          if(e.getMessage()==null || !e.getMessage().startsWith("Missing unknown "))
            exc = CoreUtils.getTrace(e);
        }
        if(pc == null || pc.getFirst() == null){
          //ok = revisionInfo.commit;
          //TODO look onto it
          revisionInfo = man.getProjectRevisionByDate(revisionInfo.getDate());
          if(revisionInfo!=null && revisionInfo != RevisionInfo.NONE){
            pc = man.getProjectContentByRevision(this, revisionInfo, revisionInfo.getRevision(), revisionInfo.getRevision(db), waitLoaded, mon);
            key = ok;
          }
        }
        if(pc!=null){
          exc = null;
          mon.beginTask("Start loading revision "+(revisionInfo==null?"NOREV":revisionInfo.commit.toString())+" of tree "+db.getSemiLocalName(), 10000);
          try{
            tree = loadTree(db, revisionInfo, pc.getFirst(), load, true, initCache, waitLoaded, mon);//TODO maybe not check?
            bindTreeToProject(pc, tree, revisionInfo, lname, pc.getFirst());
            //db.getTracker().getTreeStateManager().getRevisionInfo(tree);
            if(tree!=null){
//              TreeDB old = map.put(key, tree);
//              if(old!=tree && old!=null)
//               rt.getRevisionsTracker().stopTracking(old, old.revision, false);
            }
          }finally
          {
            mon.doneTask();
          }
        }else if (revisionInfo == RevisionInfo.NONE){
          //TODO more?
        }
      }
    } catch( com.unitesk.requality.repo.CVSException cvs){
      RLogger.logError(cvs);
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      RLogger.logError(e);
    }
    if(exc!=null)
      RLogger.logError(exc);
    return new Pair<>(revisionInfo, tree);
  }

  public static void forgetCPCaches(String pname) {
    Path cf = CoreUtils.getCacheDir(ResManager.getManager().getTracker().getGlobalCacheLocation());
    cf = cf.resolve(CoreUtils.CACHE_FOLDER_NAME);//cp folder
    String projName = pname;
  try {
      Path cpList = cf.resolve("cpConversion");
      if(Files.exists(cpList)) {
        List<String> lines = Files.readAllLines(cpList);
        List<String> newLines = new LinkedList<>();
        for(String line: lines) {
          int sepPos = line.indexOf("|");//TODO const for |
          if(sepPos>-1) {//data pos
            String fileName = line.substring(0,sepPos).trim();
            String fileId = line.substring(sepPos+1).trim();
            if(fileId.contains(projName+"-")){//TODO const for -
              CoreUtils.delete(cf.resolve(fileName+".db").toFile(), true, true);//can drop connections
              continue;
            }
            newLines.add(line);
          }
        }
        if(lines.size()!=newLines.size()) 
          Files.write(cpList, newLines, StandardOpenOption.TRUNCATE_EXISTING);//rewrite general file
      }//if not exist then we not need to update it
    } catch (IOException e) {
      RLogger.logError(e);
    }//name of cp names file
  }
  public static Path getRevisionFolder(String projLocalName){
    return getRevisionFolder(projLocalName, null);
  }
  public static Path getRevisionFolder(String projLocalName, File rootFolder){
//    if(db2==null)
//      return null;
    if(rootFolder == null) 
      rootFolder = CoreUtils.getCacheDir(ResManager.getManager().getTracker().getGlobalCacheLocation())
         .resolve(RepoTracker.REPO_TRACKER_FOLDER).toFile(); 
    if(rootFolder!=null)
      return rootFolder.toPath().resolve(projLocalName);
    return null;
  }
  @Override
  public TreeStateManager getTreeStateManager() {
    return rt == null ? null : rt.getTreeStateManager();
  }

  public static TreeDB loadTreeDB(IResTracker tracker, IResTracker treeDefinedTracker, TreeDB root, File ch, RevisionInfo ri, boolean load, boolean check, boolean initCache, boolean waitLoaded, IProgressMonitor mon) {
  //  if(rt.updateVersions(ch)){
//      TreeDB db = TreeDB.load(this, new FSStorage(ch.toURI().toString()));
//      initClasses(db);
      //dbs.put(ch.getName(), db);
    TreeDB db = null;
    String lname = ch.getName();
    try {
      db = tracker.getTreeDB(ch.toURI(), ri, lname);
    } catch (IOException e1) {
    // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if(db!=null && !db.isDisposed())
      return db;
    try {

      TreeStateManager tsm = tracker.getTreeStateManager();
      {
        mon.beginTask("Load project "+lname+" description", mon.getMax());
        TreeState state = tsm.getState(lname, ri);
        if(state == TreeState.LOADED || state == TreeState.LOADED_NO_CACHE || state == TreeState.READY) { 
          db = tracker.getTreeDB(ch.toURI(), ri, lname);
          if(db!=null && !db.isDisposed())
            return db;
        }
        db = tracker.loadTreeDBDescription(ch.toURI(), ri, false, load, treeDefinedTracker, mon);
        db.revision = ri;
        if(state != TreeState.NOT_LOADED) {
          tsm.setRevisionInfo(db, ri);
          tsm.setState(lname, null, db, TreeState.NOT_LOADED);//TODO may cause npe
        }
        mon.doneTask();
        if(ch.toPath().startsWith(db.getTracker().getRootLocation())) {
          //db.revision = ri;
          mon.beginTask("Load project "+lname+" - notify listeners before", 1);
          tracker.notifyListeners(Moment.BEFORE, lname, null, db, TreeEvent.TREE_OPEN);//Update state on
          mon.doneTask();
          
          TreeStateManager.setSliceInfo(db, RepoTracker.getRepoTracker(tracker).getSlice());//TODO check?
          OperationResult tlr = tracker.trackTreeDB(ch.toURI(), db.getLocalName(),null,false,waitLoaded,check, initCache, db);
          if(tlr == OperationResult.PARALLEL) {//TODO unsucced?
            return null;
          }
          if(tlr == OperationResult.UNSUCEEDED){
            CacheStorage.closeRecConnection(ch);
            return null;
          }
          mon.beginTask("Load project "+lname+" - notify listeners after", 1);
          tracker.notifyListeners(Moment.AFTER, lname, null, db, TreeEvent.TREE_OPEN);//Update state on
          mon.doneTask();
        }
      }
    } catch (IOException e) {
    // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    if(db == null)
      return null;
    Lock wl = db.getStorage().writeLock(null);
//    CacheTransactionDescription a = null;
//    CacheStorage cs = tracker.getCPCacheStorage(db);
    try{
//      mon.beginTask("Load project - remove temp instsances", 1);
//      a = cs.startCacheTransaction("init tree "+ch.toURI().toString(), CacheStorage.COMMIT_ALL);
//      mon.doneTask();
//      mon.beginTask("Load project - load DBS", 1);
//      db.getLinkResolver().initCaches();
//      db.getTermsStorage().initCaches();
      return db;
    }
    catch(Exception r){
      RLogger.logError(r);
      return null;
    }
    finally{
//      mon.doneTask();
//      if(a!=null&&a.error==null)
//        cs.commit(a);
      wl.unlock();
    }
  }
  private TreeDB loadTree(TreeDB db, RevisionInfo rev, File pc, boolean load, boolean check, boolean initCache, boolean waitLoaded, IProgressMonitor mon) {
    TreeDB dba = loadTreeDB(this, this, db, pc, rev, load, check, initCache, waitLoaded, mon);
    return dba;
  }

  public List<FileSpaceTimePoint> getRevisions(TreeNode node) {
    return getRevisions(node, null);
  }
  
  public List<FileSpaceTimePoint> getRevisions(TreeNode node, String rev) {
    errorsInfo = new HashMap<>();
    if(node!=null)
      try {
        ICVSClient man = getCVSClient();
        List<FileSpaceTimePoint> s = new ArrayList<>();
        s.addAll(man.getResourceRevisions(node, rev, null));
        //s.addAll(getRevisions(man, (IContainer)node));
         return s;
      } catch (Exception e) {
        gatherErrorMsg(e.getLocalizedMessage(), e);
        RLogger.logError(e);
      }
    return Collections.emptyList();
  }

  public Iterator<FileSpaceTimePoint> getRevisions(TreeDB p) {
    try {
      ICVSClient man = getCVSClient();
      if (man == null) return Collections.emptyIterator();
      Iterator<FileSpaceTimePoint> it = man.getResourceRevisions(p, null);
      if(it == null)
        return Collections.emptyIterator();
      return it;
//      DeepFirstTreeWalker dftw =
//        new DeepFirstTreeWalker(p.getRootNode(),false,false,true,true,false);
//      Iterator<TreeNode> it = dftw.iterator();
//      while(it.hasNext()){
//        TreeNode node = it.next();
//        if(node == null)
//          continue;
//        List<FileSpaceTimePoint> set = getRevisions(node);
//        if(set!=null)
//          set.stream().forEach(one->{if(!list.containsKey(one.getRevision()))
//                                  list.put(one.getRevision(), one);});
//      }
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      RLogger.logError(e);
    }
    return Collections.emptyIterator();
  }

  public int getLastErrorCode() {
    return lastError;
  }
  ICVSClient svcClient = null;
  public static ICVSClient getCVSClient(IResTracker tracker, URI uri, String lname) {
    int lastError = 0;
    ICVSClient svcClient = null;
    try {
      IRepoMapper teamProvider = API.getRepoMapping(uri);
      if (teamProvider == null) {
        //if(withLog) log.addLogLine("No team provider");
        throw new CVSException( CVSException.CVCLMANAGER_NOTEAMPROVIDER,
            "There is no team provider assigned to the baseTree, CVCL manager cannot work without it.");
      }
  
      svcClient = API.getRepositoryProvider(teamProvider.getRepoProviderId(uri));
      if (svcClient == null) {
        throw new CVSException(CVSException.CVCLMANAGER_NOTIMPLEMENTED, "There is no support for the given team provider("+
            teamProvider.getRepoProviderId(uri)+")");
      }
      svcClient = svcClient.getClass().getConstructor(IResTracker.class,URI.class, String.class).newInstance(tracker, uri, lname);
    } catch (CVSException e) {
      RLogger.logDebug(CoreUtils.getTrace(e));//common case
    }
    catch (Exception e) {
      RLogger.logError(e);
    }finally {
      return svcClient;
    }
  }
  public boolean testCVSClientChanged(TreeDB db) {
    ICVSClient clientNew = null;
    int newErrorCode = 0;
    try {
      clientNew = getCVSClient(db);
    }catch(CVSException e) {
      newErrorCode = e.getCode();
    }catch(Exception e) {
    }
    if(newErrorCode!=lastError || (clientNew==null)!=(svcClient==null))
      return true;
    return clientNew!=svcClient;//TODO more db related changes?
  }
  public ICVSClient getCVSClient() throws Exception {
    if(db == null)
      return null;
    if(lastError == CVSException.CVCLMANAGER_NOTEAMPROVIDER) {
      throw new CVSException(lastError,
        "There is no team provider assigned to the baseTree, CVCL manager cannot work without it.");
    }
    if(svcClient!=null && svcClient.getProjectName()!=null && svcClient.getProjectName().equals(db.getProjectName()))
      return svcClient;
    try {
      ICVSClient cli = getCVSClient(db);
      if(cli!=null)
        svcClient = cli;
      return svcClient;
    }catch(CVSException cvse) {
      lastError = cvse.getCode();
      throw cvse;
    }
  }
  public static ICVSClient getCVSClient(TreeDB db) throws Exception {
    IRepoMapper teamProvider = API.getRepoMapping(db);
    if (teamProvider == null) {
      throw new CVSException(CVSException.CVCLMANAGER_NOTEAMPROVIDER, 
          "There is no team provider assigned to the baseTree, CVCL manager cannot work without it.");
    }

    ICVSClient svcClient = API.getRepositoryProvider(teamProvider.getRepoProviderId(db));
    if (svcClient == null) {
      throw new CVSException(CVSException.CVCLMANAGER_NOTIMPLEMENTED,
        "There is no support for the given team provider("+
          teamProvider.getRepoProviderId(db)+")");
    }
    svcClient = svcClient.getClass().getConstructor(IResTracker.class,TreeDB.class).newInstance(db.getTracker(),db);
    //svcClient.init(repoURL);
    //svcClient.init(db);
    svcClient.excludeDotReqCSVDir();
    return svcClient;
  }

  public void initManagerWithProject(TreeDB pr, boolean needLog)
      throws CVSException {
    try {
      if (pr == null) {
        throw new CVSException(CVSException.CVCLMANAGER_FAILED, "Project pointer is null");
      }
      //usrPasswdDialog = upD;
  
      //workspaceAbsolutePath = baseTree.getWorkspace().getRoot().getLocation().toFile().toString();
      projectAbsolutePath =
        (new File(ResManager.getManager().getLocation(pr)))//TODO mb wrong path
              .getAbsolutePath();
  
      // создаём рабочую папку
      workingPlace = new java.io.File(projectAbsolutePath + "/.reqcache/.reqcvs/");
      if (!workingPlace.exists())
        if (!workingPlace.mkdirs()) {
          throw new CVSException(CVSException.CVCLMANAGER_WORKINGPLACEFAILED, "Cannot create folder " + workingPlace.getAbsolutePath());
        }
  
      // создаём папку с иторией
      historyFolder = new java.io.File(workingPlace.getAbsolutePath() + "/history/");
      if (!historyFolder.exists())
        if (!historyFolder.mkdir()) {
          throw new CVSException(CVSException.CVCLMANAGER_HISTORYFOLDERFAILED, "Cannot create folder " + historyFolder.getAbsolutePath());
        }
  
      // создаём папку для временных файлов
      tempFolder = new java.io.File(workingPlace.getAbsolutePath() + "/tmp/");
      if (!tempFolder.exists())
        if (!tempFolder.mkdir()) {
          throw new CVSException(CVSException.CVCLMANAGER_TEMPFOLDERFAILED,"Cannot create folder " + tempFolder.getAbsolutePath());
        }
  
      // борт-журнал
      //выясняем, есть ли вообще team provider
      IRepoMapper teamID = API.getRepoMapping(pr);
      //RepositoryProvider teamProvider = RepositoryProvider.getProvider(baseTree);
      if (teamID == null) {
        throw new CVSException(CVSException.CVCLMANAGER_NOTEAMPROVIDER,
            "There is no team provider assigned to the baseTree, CVCL manager cannot work without it.");
      }
  
      ICVSClient svcClient = API.getRepositoryProvider(teamID.getRepoProviderId(pr));
      if (svcClient == null) {
        throw new CVSException(CVSException.CVCLMANAGER_NOTIMPLEMENTED,
            "There is no support for the given team provider");
      }
  
      try {
        //svcClient.init(repoURL);
        //svcClient.init(pr);
        svcClient.excludeDotReqCSVDir();
  
        //repoURL = svcClient.getRepoURL();
      } catch (Exception ex) {
        //ex.printStackTrace();
  
        CVSException locEx = new CVSException(
            CVSException.CVCLMANAGER_REPOINTERFACEFAILED,
            "Some exception during svcClient initialisation");
        locEx.initCause(ex);
  
        throw locEx;
      }
      lastError = CVCLMANAGER_OK;
    }catch(CVSException e) {
      lastError = e.getCode();
      throw e;
    }

  }
//  private Map<String, IgnoredManipulator> cacheMans = new HashMap<>();
  private IgnoredManipulator getCMN(TreeDB db){
    return (IgnoredManipulator)db.getCMM().getManipulator(IGNORE_CACHE_MAN);
  }
  public void setIgnoredState(TreeNode par, TreeNode node, String id, boolean ig){
    IRepoMapper rm = API.getRepoMapping(par.getTreeDB());
    if(rm!=null){
      ICVSClient svcClient;
      try {
        svcClient = getCVSClient();
        if(svcClient!=null && getIsDirectlyIgnored(par, node, id, false)!=ig){
          svcClient.setIgnore(par, id, ig);
          ((IgnoredManipulator)db.getCMM().getManipulator(IGNORE_CACHE_MAN)).setIgnored(node, ig);
          //baseTree.getCacheStorage().setCacheValue(node.getSUUId(), "repoCache", "ignoreState", ig);
        }
      } catch (Exception e) {
        gatherErrorMsg(e.getLocalizedMessage(), e);
        RLogger.logError(e);
      }
    }
  }
  public boolean getIsDirectlyIgnored(TreeNode par, TreeNode nde, String id, boolean persist) {
    IRepoMapper rm = API.getRepoMapping(nde.getTreeDB());
    if(rm!=null){
      ICVSClient svcClient;
      try {
        svcClient = getCVSClient();
        if(svcClient!=null){
          IgnoredManipulator im = getCMN(db);
          if(im == null)
            return false;
          Boolean o = null;
          try {
            o = im.getIgnored(nde);
          }catch(Exception nosql) {
            RLogger.logDebug(CoreUtils.getTrace(nosql));
          }
          if(o == null && par!=null){
            boolean ig = svcClient.getIsIgnored(par, id);
            if(persist)
              svcClient.setIgnore(par, id, ig);
            //TODO look
            //getCMN(baseTree).setIgnored(nde, id);
            return ig;
          }else
            return o == null?false:o;
        }
      } catch (CVSException e) {
        RLogger.logDebug(CoreUtils.getTrace(e),10);
      } catch (Exception e) {
        gatherErrorMsg(e.getLocalizedMessage(), e);
        RLogger.logError(e);
      }
    }
    return false;
  }

  @Override
  public void changed(List<TreeNodeChangeEvent> changes) {
    //gui one
  }

  @Override
  public void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {

    TreeNode par = node.getWorkParent();
    if(params.contains(TreeNode.ATTR_ID))
      if(m == Moment.AFTER){
        String olda = params.getOld(TreeNode.ATTR_ID).getValue().toString(),
            newa = params.getNew(TreeNode.ATTR_ID).getValue().toString();
        if(getIsDirectlyIgnored(par, node, olda, false)){
          setIgnoredState(par, node, olda, false);
          setIgnoredState(par, node, newa, true);
        }
      }
    if(m == Moment.BEFORE)
      if(params.contains(TreeNode.ATTR_REPO_STATE)){
        Attribute n = params.getNew(TreeNode.ATTR_REPO_STATE);
        Attribute o = params.getOld(TreeNode.ATTR_REPO_STATE);
        UUID uid = null;
        if(n!=null)
          uid = n.getOrigin();
        else
          if(o!=null)
            uid = o.getOrigin();
        String old = node.getId();
        if(params.contains(TreeNode.ATTR_ID))
          old = params.getOld(TreeNode.ATTR_ID).getValue().toString();
        if(uid!=null && node.getUUId().equals(uid)){
          if(n!=null && n.getValue()!=null && n.getValue().toString().equals("true"))
            setIgnoredState(par, node, old, true);
          else
            setIgnoredState(par, node, old, false);
        }
      }
  }

  @Override
  public void beforeCreated(NodeDesc node, byte flag) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void created(TreeNode node, byte flag) {
    TreeNode par = node.getWorkParent();
    if(par!=null)
      if(bupDel.contains(node.getUUId())){
        bupDel.remove(node.getUUId());
        setIgnoredState(par, node, node.getId(), true);
        node.updateLocalAttribute(TreeNode.ATTR_REPO_STATE);
        node.commitAttributes();
      }
  }

  @Override
  public void childrenOrderChanged(TreeNode node, byte flag) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void deleted(TreeNode node, byte flag, Moment m) {
    TreeNode par = node.getWorkParent();
    if(par!=null)
      if(m == Moment.BEFORE && getIsDirectlyIgnored(par, node, node.getId(), false)){
        bupDel.add(node.getUUId());
        setIgnoredState(par, node, node.getId(), false);
      }
  }

  @Override
  public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
    boolean sta = getIsDirectlyIgnored(oldParent, node, node.getId(), false);
    if(sta){
      if(m == Moment.BEFORE)
        setIgnoredState(oldParent, node, node.getId(), false);
      if(m == Moment.AFTER)
        setIgnoredState(newParent, node, node.getId(), sta);
    }
  }
  private RevisionInfo getRevisionInfo(TreeDB db) {
    return TreeStateManager.getRevisionInfo(db);
  }

  @Override
  public TreeDB[] getTreeDBs() {
    return rt.getTreeDBs();
  }

  @Override
  public TreeDB createProject(String lname, String pname) {
    return rt.createProject(lname, pname);
  }

  @Override
  public void init(Pair<String, Object>... params) {
  
  }

  @Override
  public String getLocalName(TreeDB db) {
    return rt.getLocalName(db);
  }
  @Override
  public String getProjectName(TreeDB db) {
    return rt.getProjectName(db);
  }
  @Override
  public String getVisibleName(TreeDB db) {
    return rt.getVisibleName(db);
  }

  @Override
  public TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean loadCache, IProgressMonitor mon) {
    TreeDB ret = null;
    if(dbFrom == null)
      dbFrom = db;
    if(dbFrom == null || dbFrom.getProjectName() == null)
      return null;
    if(pname!=null && dbFrom.getProjectName().equals(pname)) {
      if(isAvailiable(dbFrom))
        return dbFrom;
      else
        return null;
    }
    if(lname!=null && dbFrom.getLocalName().equals(lname))
    {
      if(isAvailiable(dbFrom))
          return dbFrom;
        else
          return null;
    }
    RevisionInfo ri = TreeStateManager.getRevisionInfo(dbFrom);
    TreeDB adb = rt.getTreeDB(dbFrom, lname, pname, load, loadCache, mon);
    Pair<RevisionInfo, TreeDB> radb = null;
    if(adb!=null) {
      if(adb.revision!=null && adb.revision!=RevisionInfo.NONE && adb.revision.getRevision(adb).equals(ri.getRevision(adb)))
        if(isAvailiable(adb))
          return adb;//sliced node is the required one
      if(ri!=null)
        radb = Global.getRepoManager(adb).getRevisionTree(ri, load, loadCache, mon);
    }
    if(radb!=null && radb.getSecond()!=null) {
      if(isAvailiable(radb.getSecond()))
          return radb.getSecond();
        else
          return null;
    }
    if(adb!=null && isAvailiable(adb))
      return adb;
    else
      return null;
  }

  private boolean isAvailiable(TreeDB dbFrom) {
    RevisionInfo ri = TreeStateManager.getRevisionInfo(dbFrom);
    if(ri==null || ri == RevisionInfo.NO_REV)
      return rt.getBase().getTreeStateManager().isAvailiable(dbFrom.getLocalName(), ri);
    return getTreeStateManager().isAvailiable(dbFrom.getLocalName(), ri);
  }

  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage,
      IResTracker repoManager, IProgressMonitor mon) throws IOException {
    TreeDB db = rt.loadTreeDBDescription(path, ri, ignorev, ignorestorage, repoManager, mon);
    db.setTracker(this);
    return db;
  }


  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage, IResTracker repoManager)
      throws IOException {
    TreeDB db = rt.getTreeDB(path, ri, ignorev, ignorestorage,repoManager);
    if(db == null || db.isDisposed())
      return null;
  //Can change underlined stored treedb
    db.setTracker(this);
    return db;
  }

  @Override
  public String getLocalName(URI path) {
    return rt.getLocalName(path);
  }

  @Override
  public String getProjectName(URI path) {
    return rt.getProjectName(path);
  }

  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, String name, IProgressMonitor mon) throws IOException {
    TreeDB db = rt.loadTreeDBDescription(path, ri, name, mon);
    db.setTracker(this);
    return db;
  }

  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, String name) throws IOException {
    TreeDB db = rt.getTreeDB(path, ri, name);//Can change underlined stored treedb
    if(db == null)
      return null;
    db.setTracker(this);
    return db;
  }

  @Override
  public TreeDB loadTreeDBDescription(Object project, RevisionInfo ri, String name, IProgressMonitor mon) throws IOException {
    TreeDB db = rt.loadTreeDBDescription(project, ri, name, mon);
    db.setTracker(this);
    return db;
  }

  @Override
  public TreeDB getTreeDB(Object proj, RevisionInfo ri, String name) throws IOException {
  TreeDB db = rt.getTreeDB(proj, ri, name);//Can change underlined stored treedb
    if(db.isDisposed())
      return null;
  db.setTracker(this);
    return db;
  }

  @Override
  public void initDBLoading(Object proj, RevisionInfo ri, TreeDB db) {
    rt.initDBLoading(proj, ri, db);
  }

  @Override
  public String getLocalName(Object proj) {
    return rt.getLocalName(proj);
  }

  @Override
  public OperationResult sourceSpecificTrackTreeDB(Object proj, String lname, String pname, boolean ignoreVersion,boolean noParallel,boolean check, boolean initCache, TreeDB db) {
    IProgressMonitor mon = (db==null||db.getMonitor().isCanceled())?new ConsoleProgressMonitor():db.getMonitor();
    Instantiator.removeTempInstances(db, mon);
    Instantiator.loadDBs(db, mon);
    return rt.sourceSpecificTrackTreeDB(proj, lname, pname, ignoreVersion,noParallel,check, initCache, db);
  }

  @Override
  public boolean isOpen(Object project) {
    return rt.isOpen(project);
  }

  @Override
  public void close(Object project) {
    rt.close(project);
  }

  @Override
  public CheckerContainer getCheckerContainer(TreeDB newDB, RevisionInfo ri) {
    return rt.getCheckerContainer(newDB, ri);
  }

  @Override
  public void remove(String lname, String pname) {
    rt.remove(lname,pname);
  }
  
  @Override
  public String getLocation(Object proj) {
    return rt.getLocation(proj);
  }
  @Override
  public String getLocation(TreeDB treeDB, boolean noCache) {
    return rt.getLocation(treeDB, noCache);
  }
  
  @Override
  public Map<String, Template> getReportTemplates(TreeDB db, TemplateScope ts) {
    return rt.getReportTemplates(db, null);
  }
  

  @Override
  public Map<String, Group> getReportGroups(TreeDB db, TemplateScope ts) {
    return rt.getReportGroups(db, ts);
  }
  
  @Override
  public void clearCache(TreeDB p, String localName, boolean delete) {
//    for(TreeDB db:map.values())
//      db.clearCaches(delete, false);
    rt.clearCache(p, localName, delete);
  }
    
  @Override
  public void initClasses(TreeDB db) {
    rt.initClasses(db);
  }
  
  @Override
  public Path getRootLocation() {
    return rt.getRootLocation();
  }
  
  @Override
  public URI getGlobalCacheLocation() {
    if(TreeStateManager.getRevisionInfo(db)!=null)
      return rt.getGlobalCacheLocation();
    return rt.getBase().getGlobalCacheLocation();
  }
  
  @Override
  public void close(TreeDB db) {
    rt.close(db);
  }
  
  @Override
  public void refresh(TreeDB project, TreeNode resource, int depth) {
    rt.refresh(project, resource, depth);
  }
  @Override
  public Object getStoringEntity(TreeDB treeDB) {
    return rt.getStoringEntity(treeDB);
    //TODO mb repo man specific one
  }
  @Override
  public void refresh(Object storingEnt, int depth) throws Exception {
    rt.refresh(storingEnt, depth);
  }
  
  @Override
  public boolean addListener(Moment m, ITreeStateListener l) {
    return rt.addListener(m, l);
  }
  
  @Override
  public boolean removeListener(Moment m, ITreeStateListener l) {
    return rt.removeListener(m, l);
  }
  
  @Override
  public synchronized void notifyListeners(Moment m, String lname, String pname, TreeDB db, TreeEvent type) {
    rt.notifyListeners(m,lname,pname, db, type);
  }
  
  @Override
  public void removeTemplate(File to, TreeDB db, TemplateScope ts) {
    rt.removeTemplate(to, db, ts);
  }
  
  @Override
  public File addReportTemplate(File from, TreeDB db, TemplateScope ts) {
    return rt.addReportTemplate(from, db, ts);
  }
  
  @Override
  public CacheStorage getCPCacheStorage(TreeDB dbfrom) {
    if(TreeStateManager.getRevisionInfo(dbfrom)!=null)
      return rt.getCPCacheStorage(dbfrom);
    return rt.getBase().getCPCacheStorage(dbfrom);
  }
  
  public RevisionInfo getRevisionInfoByRev(String rev, IProgressMonitor mon) throws Exception{
    ICVSClient cv = null;
    errorsInfo = new HashMap<>();
    try {
      cv = getCVSClient();
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      RLogger.logDebug(CoreUtils.getTrace(e));
    }
    if(cv == null)
      return null;
    return cv.getRevisionInfo(rev, mon);
  }
  public RevisionInfo getRevisionInfoByDate(Date rev) {
    ICVSClient cv = null;
    try {
      cv = getCVSClient();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      RLogger.logDebug(CoreUtils.getTrace(e));
    }
    if(cv == null)
      return null;
    try {
      return cv.getProjectRevisionByDate(rev);
    } catch (Exception e) {
      RLogger.logDebug(CoreUtils.getTrace(e));
    }
    return null;
  }

  public RepoTracker getRepoTracker() {
    return rt;
  }

  @Override
  public void disableListeners(byte b) {
    rt.disableListeners(b);
  }

  @Override
  public void enableListeners(byte b) {
    rt.enableListeners(b);
  }

  @Override
  public boolean isDisabledListening(byte b) {
    return rt.isDisabledListening(b);
  }

  @Override
  public void open(Object obj) {
    rt.open(obj);
  }

  public RevisionInfo getHeadRevision() {
    ICVSClient cli;
    try {
      cli = getCVSClient();
      RevisionInfo rev = cli.getHeadRevision(db);
      if(rev!=null)
        return rev;
      return null; 
    } catch (CVSException e) {
      //tends to be not git project
    } catch(Exception e2){
      RLogger.logError(e2);
    }
    return null;
    
  }
  //private static Map<TreeDB, > state = new HashMap<>();
  private static String META_REPO_TREE_STATE="REPO_MANAGER_TREE_STATE";
  public static RepoTreeState<FSStorage, UUID> getTreeByPath(TreeDB db, String dbName, String s) {
    ICVSClient cli;
    String id = dbName+"-"+s;
    if(db == null)
      return null;
    Map<String, RepoTreeState<FSStorage, UUID>> state = db.getMetaObject(META_REPO_TREE_STATE);
    if(state == null)
      synchronized (META_REPO_TREE_STATE) {
        state = db.getMetaObject(META_REPO_TREE_STATE);
        if(state==null) {
          state = new HashMap<>();
          db.registerMetaObject(META_REPO_TREE_STATE, state);
        }
      }
    if(state.containsKey(id))
      return state.get(id);
    try {
      //TOOD sync?
      cli = Global.getRepoManager(db).getCVSClient();
      RepoTreeState<File, String> containerResult = null;
      synchronized (cli) {
        if(state.containsKey(id))
          return state.get(id);
      }
      containerResult = cli.getContainerByPath(s, db);
      if(containerResult!=null && containerResult.storage!=null&&containerResult.storage.exists()){
        FSStorage fs = new FSStorage(containerResult.storage.toURI(), null);//TODO IDE monitor
        //fs.initCaches(fs, CacheInitializationStage.PRE_INIT, fs.getMonitor());//TODO mb -1
        Map<UUID, String> rev = new HashMap<>();
        Map<String,String> save = new HashMap<>(containerResult.retRevisions);
        dfsUUIDs(rev, fs, fs.getRootNode(), containerResult.retRevisions, "root",null);
        if(containerResult.retRevisions.size()!=0){
          String find = containerResult.retRevisions.keySet().iterator().next();
          containerResult.retRevisions.putAll(save);
          rev.clear();
          dfsUUIDs(rev, fs, fs.getRootNode(), containerResult.retRevisions, "root", find.substring(0,find.lastIndexOf("/")));
          throw new RuntimeException("missmatch number of processed nodes in collected tree "+containerResult.retRevisions.size());
        }
        state.put(id, new RepoTreeState<>(containerResult.revision, fs, rev));
        return state.get(id);
//          FilesTracker ft = new FilesTracker();
//          ft.init(new Pair<String, Object>(ResManager.PATH, path.getParentFile().toURI().toString()),new Pair<String, Object>(ResManager.TEMPL_PATH,""));
//          return ft.getTreeDB(null, path.getName(), true);
      }
    }
    catch(Exception e){
      RLogger.logError(e);
    }
    RLogger.logDebug("Error loading "+id); 
    state.put(id,null);
    return null;
  }

  private static void dfsUUIDs(Map<UUID, String> rev, FSStorage fs, UUID node, Map<String,String> pathrev, String path,String fibd) {
    //if(fibd!=null && (path+"/"+fs.getNodeId(node)).toLowerCase().startsWith(fibd.substring(0,fibd.length()-10)))
      //System.out.println(1 ); 
    Collection<UUID> cl = fs.getChildren(node);
    String nid = fs.getNodeId(node);
    String apath = path;
    if(nid == null ||!nid.equals("/")){
      if(nid!=null && !nid.isEmpty()){
        if(!apath.isEmpty())
          apath+="/";
        apath += nid;
      }else
        RLogger.logDebug("nid is empty during nfs on "+apath);
    }
    String prev = pathrev.remove(apath.toLowerCase());
    if(prev!=null)
      if(!rev.containsKey(node))
        rev.put(node, prev);
      else
        RLogger.logDebug("rev already have "+node); 
    //else if(!apath.isEmpty())
      //System.out.println("Path not loaded! "+apath );
    for(UUID uid:cl)
      dfsUUIDs(rev, fs, uid, pathrev, apath,fibd);
    if(!rev.containsKey(node))
      RLogger.logDebug("node is not in rev "+node); 
  }

  @Override
  public CacheStorage getCacheStorage(String stateModelName) {
    return rt.getCacheStorage(stateModelName);
  }

  @Override
  public CacheStorage getCacheStorage(TreeDB dbfrom) {
    return rt.getCacheStorage(dbfrom);
  }
  @Override
  public CPLinkResolver getCPLinkResolver(TreeDB dbfrom) {
    if(TreeStateManager.getRevisionInfo(dbfrom)!=null)
      return rt.getCPLinkResolver(dbfrom);
    return rt.getBase().getCPLinkResolver(dbfrom);
  }

  @Override
  public synchronized TrackerCacheManipulatorsManager getCMM(TreeDB dbfrom) {
    if(TreeStateManager.getRevisionInfo(dbfrom)!=null)
      return rt.getCMM(dbfrom);
    return rt.getBase().getCMM(dbfrom);
  }

  @Override
  public void clearNodeCache(TreeDB p, TreeNode node, boolean delete) {
    rt.clearNodeCache(p, node, delete);
  }

  @Override
  public Map<String, CacheManipulator<TreeDB>> getAPIDBPersistCacheManipulator(
      TreeDB treeDB, SettingsManipulator sm) {
    Map<String, CacheManipulator<TreeDB>> man = new HashMap<>();
    IgnoredManipulator im = new IgnoredManipulator(treeDB);
    man.put(IGNORE_CACHE_MAN, im);
    return man;
  }

  @Override
  public Map<String, CacheManipulator<TreeDB>> getAPIDBCacheManipulator(
      TreeDB treeDB, SettingsManipulator sm) {
    return null;
  }

  @Override
  public String getId() {
    return "RepoManagerIgnoreProvider";
  }

  @Override
  public ProjectLoadingState getProjectLoadingState(URI uri, String pname) {
    return rt.getProjectLoadingState(uri, pname);
  }
  @Override
  public ProjectLoadingState setProjectLoadingState(URI uri, String pname, ProjectLoadingState state) {
    return rt.setProjectLoadingState(uri, pname, state);
  }


  @Override
  public Object getProjectEntity(String lname, String pname, boolean onlyDB) {
    return rt.getProjectEntity(lname, null, true);
  }

  public TreeDB getTreeDB() {
    return db;
  }

  public void replaceTreeDB(TreeDB db2) {
    this.db = db2;
    svcClient = null;
  }

  @Override
  public String getJSResourcesPath() {
  return rt.getJSResourcesPath();
  }

  @Override
  public void markNonLoadable(URI uri) {  
  if(rt instanceof AbstractResTracker)
    ((AbstractResTracker)rt).markNonLoadable(uri);
  }

  @Override
  public void setGUISettings(boolean noGUI, boolean alwaysOkOnNoGui) {
    rt.setGUISettings(noGUI, alwaysOkOnNoGui);
  }

  @Override
  public void bindTreeToProject(Object proj, TreeDB db, RevisionInfo ri, String lname, File ch) {
    rt.bindTreeToProject(proj, db, ri, lname, ch);
  }

  @Override
  public void registerNames(TreeDB db, String lname, String pname) {
    rt.registerNames(db, lname, pname);
  }

  @Override
  public Collection<File> getAllResourceProjects() {
    return rt.getAllResourceProjects();
  }

  @Override
  public Pair<Boolean, TreeDB> handleProjectRename(Path p, TreeDB project, String localName, String projectNameOld, String projectNameNew, boolean ignoreMessages, IProgressMonitor mon) {
    return rt.handleProjectRename(p, project, localName, projectNameOld, projectNameNew, ignoreMessages, mon); 
  }
  @Override 
  public ReentrantReadWriteLock getProjectLoadingLock(){
    return rt.getProjectLoadingLock();
  }

  @Override
  public OperationResult handleNewProject(URI loc, String lname, boolean parallel, boolean ignoreMessages) {
    return rt.handleNewProject(loc, lname, parallel, ignoreMessages);
  }

  @Override
  public void handleNewProjects(List<URI> reqProjects) {  
    rt.handleNewProjects(reqProjects);
  }

}
