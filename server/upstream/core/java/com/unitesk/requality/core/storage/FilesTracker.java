package com.unitesk.requality.core.storage;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.unitesk.requality.core.api.IAPICacheProvider;
import com.unitesk.requality.core.attribute.reference.RelationsTreeContext;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ErrorNode;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.RestrictedFolder;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.Groups;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class FilesTracker extends AbstractResTracker {
  private static final String projEx =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription>\n<name>{name}</name>\n<comment></comment>\n<projects>\n</projects>\n"
          + "<buildSpec>\n<buildCommand>\n<name>com.unitesk.requality.checkers</name>\n<arguments>\n</arguments>\n</buildCommand>\n</buildSpec><natures><nature>com.unitesk.requality.natures.reqcatalogue</nature>\n"
          + "</natures>\n</projectDescription>";
  private static String META_LOCAL_NAME="LOCAL_NAME";
  private static final String NOREV = "";
  public static final ResManagerProvider PROVIDER = new ResManagerProvider() {
    
    @Override
    public IResTracker createTracker(Pair<String, Object>... params) {
      return new FilesTracker(DBNamesManager.DEFAULT);
    }
  };
  private String path = null, templpath = null;;
  private java.nio.file.Path ppath = null;
  private URI lppath = null;
  private Map<String, Map<String,TreeDB>> dbs;
  private File rootFolder = null;
  //protected Map<TreeDB, DBLocalName> dblocalnames;
  protected Map<String, Map<String,java.nio.file.Path>> locationsP;
  private Set<String> allowedNames = new HashSet<>(), forcedRawNames = new HashSet<>();
  private DBNamesManager dbnm;
  private boolean reloadTriggered = false;
  private boolean dropCacheOnInit = false;
  public ConsoleProgressMonitor monitor = new ConsoleProgressMonitor();
  private TrackerNotifier tn = new TrackerNotifier(){
    @Override
    protected void treeClosed(TreeDB db){
      treeCloseReaction(db);
    }
    @Override
    protected void treeOpenned(TreeDB db){
    }
    @Override
    protected void treeDeleted(TreeDB db){
      treeClosed(db);
    }
  };
  public FilesTracker(DBNamesManager dbnm) {
    super();
    noGUI = true;//remove gui calls 
    alwaysOK = true;
      this.dbnm = dbnm;
  }
  public void treeCloseReaction(TreeDB db)
  {
    DBLocalName name = db.getMetaObject(META_LOCAL_NAME);
    if(name!=null) {
      RevisionInfo ri = getTreeStateManager().getRevisionInfo(db);
      String revKey = getRevKey(ri);
  //        Map<String, Path> lmap = locationsP.get(name.name);
  //        if(lmap!=null) {
  //          lmap.remove(revKey);
  //        }
      Map<String, TreeDB> map = dbs.get(name.name);
      if(map!=null) {
        map.remove(revKey);
        if(map.size() == 0)
          dbs.remove(name.name);
      }
    }
    dbnm.forget(db);
  }
  @Override
  public TreeDB[] getTreeDBs() {
    checkReloadState();
    TreeDB[]ret = new TreeDB[dbs.size()];
    int i = 0;
    for(Map<String, TreeDB> dba:dbs.values()) {
      TreeDB db = dba.get(NOREV);
      if(db!=null)
      ret[i++]=db;
      else {
      for(TreeDB dbs:dba.values())
        if(dbs!=null) {
          ret[i++] = dbs;
          break;
        }
      }
    }
    return ret;
  }

  private synchronized void checkReloadState() {
    if(!reloadTriggered) {
      reloadTriggered = true;
      reload(dropCacheOnInit);
    }
  }
  @Override
  public OperationResult handleNewProject(URI loc, String lname, boolean parallel, boolean ignoreMessages) {
    File projL = new File(loc);
    String ln = lname, pn = CoreUtils.getProjectName(projL);
    RevisionInfo ri = loadRI(projL);
    TreeDB db;
    try {

      IProgressMonitor mon = monitor;//TODO IDE monitor?
      db = loadTreeDBDescription(loc, ri, lname, mon);
      OperationResult or = trackTreeDB(projL, ln, pn, !parallel,true,true, true, db);
      if(or == OperationResult.UNSUCEEDED) {
        RLogger.logError("Attempt to load "+ln+" was failed for path "+projL.toPath().toString());
        CacheStorage.closeRecConnection(new File(loc));
      }else
        if(or == OperationResult.PARALLEL) {
          RLogger.logDebug("Loading of projct "+ln+" will be continued by version update");
        }
      return or;
    } catch (Exception e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    return OperationResult.UNSUCEEDED;
  }
  @Override
  public TreeDB createProject(String lname, String pname) {
    checkReloadState();
    File f = new File(URI.create(path + "/" + lname));
    if (f.exists())
      throw new RuntimeException("Project with name '" + lname + "' already exists");
    f.mkdirs();
    //TODO change to db?
    TreeStateManager.getOne(this).setStateWithoutDB(lname, pname, RevisionInfo.NO_REV, TreeState.NOT_LOADED);//Reset state
    TreeDB db = TreeDB.createTreeInstance(lname,pname, this, new FSStorage(f.toURI(), null));//TODO IDE monitor
    emulateDotProjectCreate(pname, new File(URI.create(path + "/" + lname + "/" + ".project")));
    
    trackTreeDB(f, lname, pname,true,true,false, true, db);
    //loadDB(lname,pname, null, f, this, false, false);
    RevisionInfo ri = getTreeStateManager().getRevisionInfo(db);
    
    TreeDB dba = dbs.get(lname).get(getRevKey(ri));
    dba.getCheckerContainer(ri).attachGuiListener(dba);
    return dba;
  }

  private String getRevKey(RevisionInfo ri) {
    return ri!=null?ri.getRevision():NOREV;
  }
  private void emulateDotProjectCreate(String name, File file) {
    try {
      FileWriter fw = new FileWriter(file);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write(projEx.replace("{name}", name));
      bw.close();
      fw.close();
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }

  private static FileFilter ff =
      new FileFilter() {

        @Override
        public boolean accept(File pathname) {
          return pathname.getName().equals(".project");
        }
      };

  private static FileFilter root =
      new FileFilter() {

        @Override
        public boolean accept(File pathname) {
          return pathname.getName().equals("root.json");
        }
      };
  private boolean initStarted = false;
  @Override
  public void init(Pair<String, Object>... params) {
    if(initStarted)
      return;
    initStarted = true;
    try {
      setInited(false);
      Map<String, Object> par = new HashMap<>();
      Arrays.stream(params).forEach(one->par.put(one.getFirst(), one.getSecond()));
      if (!par.containsKey(ResManager.PATH) || !par.containsKey(ResManager.TEMPL_PATH) )
        throw new RuntimeException(
            "At least two parameters should be provided - path to workspace and path to templates folder");
      Object o = par.get(ResManager.PATH);
      path = o!=null?o.toString():"";
      o = par.get(ResManager.TEMPL_PATH);
      templpath = o!=null?o.toString():"";
      dbs = new HashMap<>();
      locationsP = new HashMap<>();
      boolean ignoreLoadCP = (boolean)par.getOrDefault("ignoreCPLoad", false);
      dropCacheOnInit = (boolean)par.getOrDefault(ResManager.DROP_CACHE_ON_INIT, false);
      reloadTriggered = false;//causes reload of projects
      allowedNames.addAll(Arrays.asList(par.getOrDefault(ResManager.ALLOWED_PROJECTS,"*").toString().toLowerCase().split(";")));
      forcedRawNames.addAll(Arrays.asList(par.getOrDefault(ResManager.FORCE_RAW_NAMES, "").toString().split(";")));
      
      rootFolder = new File(URI.create(path));
      if (!rootFolder.exists()) rootFolder.mkdirs();
      ppath = rootFolder.toPath();
      lppath = rootFolder.toURI();
      if(!ignoreLoadCP){
        cpPath = CoreUtils.getCacheDir(getGlobalCacheLocation());
        if(dropCacheOnInit && cpPath.toFile().exists()){
          Path path = cpPath;
            if(path!=null){
              try {
          CacheStorage.removeByPath(path.toFile().getCanonicalFile().toURI());
        } catch (IOException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        }
            }
          CoreUtils.delete(cpPath.toFile(), true, false);
        }
        setPath(cpPath);
        gcss = null;
      }
      //reload(ignoreLoadCP, (boolean)par.getOrDefault(ResManager.DROP_CACHE_ON_INIT, false));
//      addsHaltTrackerReaction();
      setInited(true);
    }finally {
      initStarted = false;
    }
  }

  public void reload(boolean dropcache) {
    reloadProjects(rootFolder, dropcache);
  }
  private Map<String, File> projToLoad = new ConcurrentHashMap<>(); 
  public void reloadProjects(File f, boolean dropcache) {
    //TODO clear projToLoads?
    if(f == null)
      throw new RuntimeException ("Target folder is null during File Tracker initialization");
    if(!f.exists()) {
      RLogger.logInfo ("Target folder does not exist during File Tracker initialization "+f.getPath());
      f.mkdirs();
    }
    Map<String, File> projectQueue = new HashMap<>();
    for (File ch : f.listFiles()) {
      try {
        if (ch.isDirectory()) {
          File[] fa = ch.listFiles(ff);
          if (fa != null && fa.length == 1){
            org.w3c.dom.Document doc = null;
            try {
              doc = loadInfo(fa[0]);
            } catch (IOException e) {
              // TODO Auto-generated catch block
              RLogger.logError("error loading "+fa[0].getAbsolutePath());
              RLogger.logError(e);
            } catch (SAXException e) {
              // TODO Auto-generated catch block
              RLogger.logError("error loading "+fa[0].getAbsolutePath());
              RLogger.logError(e);
            }
            if(doc!=null && findNature(doc,"com.unitesk.requality.natures.reqcatalogue")){
              File[] rt = ch.listFiles(root);
              String name = getNameFromInfo(doc); 
              if (name == null)
                name = ch.getName();
              if (rt.length == 1 && nameFiltered(name)) {
                if(!projToLoad.containsKey(name)&&( !projectQueue.containsKey(name) || forcedRawNames.contains(ch.getName())))
                  projectQueue.put(name, ch);
              }
            }
          }
        }
      }catch(Exception e) {
        RLogger.logError(e);
      }
    }
    File sidep = f.toPath().resolve(".metadata/.plugins/org.eclipse.core.resources/.projects").toFile();
    if(sidep.exists())
      for(File pp : sidep.listFiles()){
      try {
          if(pp.exists() && pp.isDirectory()){
            File loc = pp.toPath().resolve(".location").toFile();
            if(loc.exists()){
              byte[] trash = new byte[16];
              try{
                FileInputStream fis = new FileInputStream(loc);
                DataInputStream dis = new DataInputStream(fis);
                dis.read(trash);
                String sos = dis.readUTF();
                dis.close();
                fis.close();
                File ch = new File(URI.create(sos.substring(5)));
                org.w3c.dom.Document doc = loadDocByFolder(ch);
                if(doc!=null && findNature(doc,"com.unitesk.requality.natures.reqcatalogue")){
                  File[] rt = ch.listFiles(root);
                  String name = getNameFromInfo(doc); 
                  if (name == null)
                    name = ch.getName();
                  if (rt.length == 1 && nameFiltered(name)) {
                    if(!projToLoad.containsKey(name)&&(!projectQueue.containsKey(name) || forcedRawNames.contains(ch.getName())))
                      projectQueue.put(name, ch);
                  }
                }
              }
              catch(Exception e){
                RLogger.logError(e);
              }
            }
          }
      }catch(Exception e) {
        RLogger.logError(e);
      }
      }
      
    for(Entry<String, File> e:projectQueue.entrySet()) {
      String name = e.getKey();
      if(projToLoad.containsKey(name))
        continue;
      projToLoad.put(name, e.getValue());
      try {
        File projL = e.getValue();
        handleNewProject(projL.toURI(), name, false,false);
        ///TreeStateManager.getOne().setState(ln, TreeState.LOADING);
        //loadDB(ln,pn, null, projToLoad.get(ln), this, true, dropcache);//TODO db.gettracker
      }catch(Exception ec) {
        RLogger.logError(ec);
      }
    }
  }
  private RevisionInfo loadRI(File projL) {
    // TODO Auto-generated method stub
    return null;
  }
  private boolean nameFiltered(String name) {
    return allowedNames.contains("*")||allowedNames.contains(name.toLowerCase());
  }
  public static String getNameFromInfo(org.w3c.dom.Document doc) {
    NodeList nl = doc.getChildNodes();
    Node n = null;
    for(int i = 0;i< nl.getLength();i++)
    {
      Node m = nl.item(i);
      if(m.getNodeName().toLowerCase().equals("projectdescription")){
        n = m;
        break;
      }
    }
    if(n!=null){
      nl = n.getChildNodes();
      for(int i = 0;i< nl.getLength();i++)
      {
        Node m = nl.item(i);
        if(m.getNodeName().toLowerCase().equals("name")){
          return m.getTextContent();
        }
      }
    }
    return null;
  }
  private static boolean findNature(org.w3c.dom.Document doc, String string) {
    NodeList nl = doc.getChildNodes();
    Node n = null;
    for(int i = 0;i< nl.getLength();i++)
    {
      Node m = nl.item(i);
      if(m.getNodeName().toLowerCase().equals("projectdescription")){
        n = m;
        break;
      }
    }
    if(n!=null){
      nl = n.getChildNodes();
      n = null;
      for(int i = 0;i< nl.getLength();i++)
      {
        Node m = nl.item(i);
        if(m.getNodeName().toLowerCase().equals("natures")){
          n = m;
          break;
        }
      }
    }
    if(n!=null){
      nl = n.getChildNodes();
      for(int i = 0;i< nl.getLength();i++)
      {
        Node m = nl.item(i);
        if(m.getNodeName().toLowerCase().equals("nature"))
          if(m.getTextContent().toLowerCase().equals(string.toLowerCase()))
            return true;
      }
    }
    return false;
  }
 
  
  private static  DocumentBuilderFactory factory =
      DocumentBuilderFactory.newInstance();
  private DocumentBuilder builder;
  {
    try {
      builder = factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
  }
  private org.w3c.dom.Document loadInfo(File fa) throws IOException, SAXException {
    FileInputStream fis = new FileInputStream(fa);
    org.w3c.dom.Document doc = builder.parse(fis);
    fis.close();
    return doc;
  }

  @Override
  public String getVisibleName(TreeDB db) {
    return getProjectName(db);
  }
  @Override
  public String getLocalName(TreeDB db) {
    DBLocalName lname =  db.getMetaObject(META_LOCAL_NAME);
    return lname == null? null : lname.name;
  }

  @Override
  public String getProjectName(TreeDB db) {
    DBLocalName lname = db.getMetaObject(META_LOCAL_NAME);
    String pname = null;
    if(lname!=null) {
      pname = dbnm.getProjectName(lname);
      if(pname!=null)
        return pname;
    }
    RevisionInfo ri = getTreeStateManager().getRevisionInfo(db);
    Path p = locationsP.getOrDefault(lname == null?db.getLocalName():lname.name,Collections.emptyMap()).get(getRevKey(ri));
    if(p!=null) 
      pname = CoreUtils.getProjectName(p.toFile());
    return pname;
  }

  @Override
  public String getProjectName(URI path) {
    File f = new File(path);
    if(f!=null)
      return CoreUtils.getProjectName(f);
    return null;
  }
  @Override
  public TreeDB getTreeDB(TreeDB dbFrom, String lname, String pname, boolean load, boolean initCache, IProgressMonitor mon) {
    checkReloadState();
    RevisionInfo ri = TreeStateManager.getRevisionInfo(dbFrom);
    if(lname!=null) {
      Map<String, TreeDB> map = dbs.get(lname);
      if(map!=null) {
        TreeDB db = map.get(getRevKey(ri));
        if(db!=null)
          return db;
      }
    }
    //TODO load and cache init?
    if(pname!=null)
    {
      Set<DBLocalName> lnames = dbnm.getLocalNames(pname);
      for(DBLocalName s:lnames) {
        String lpart = s.name;
        TreeDB db = dbs.get(lpart).get(getRevKey(ri));
        if(db!=null)
          return db;
      }
    }
    return null;
  }

  public void recDelete(File f) {
    if (f.isDirectory()) for (File f2 : f.listFiles()) recDelete(f2);
    f.delete();
  }


  public boolean stopTracking(TreeDB tree,RevisionInfo ri, boolean deleted) {
    if (tree == null) {
      return false;
    }
    String lname = tree.getLocalName();
    String pname = tree.getProjectName();

    if(tree.revision!=ri) 
      tree = dbs.get(lname).get(getRevKey(ri));
    if (tree == null)
      return false;
    CacheStorage cs = tree.getStorage().getCacheStorage();
    try{
      ITreeStorage ts = tree.getStorage();
      if (ts instanceof TransactionStorage){
        ((TransactionStorage)ts).rollback();
        ts = ((TransactionStorage) ts).getStorage();
      }
      //Global.onlyClearCaches(db, deleted, false);
      //tends to be duplicate of notify
      AbstractTreeStorage storage = (AbstractTreeStorage) ts;
      storage.forceNow = true; //avoid creating jobs
      try{
//        ResourcesPlugin.getWorkspace()
//            .removeResourceChangeListener(storage.getResourceChangeListener());
        
        if (!deleted) {
          notifyListeners(Moment.BEFORE, lname,pname,tree, TreeEvent.TREE_CLOSE);
          notifyListeners(Moment.AFTER, lname,pname,tree, TreeEvent.TREE_CLOSE);
        } else {
          notifyListeners(Moment.BEFORE, lname,pname,tree, TreeEvent.TREE_DELETE);
          notifyListeners(Moment.AFTER, lname,pname,tree, TreeEvent.TREE_DELETE);
          //db.getStorage().getCacheStorage().remove();//already in db reaction
        }
      }finally{
        //db.setState(TreeState.NOT_LOADED);
        storage.forceNow = false;
      }
    }
    finally{
      TreeStateManager tsm = getTreeStateManager();
      //removeNewHandling(project);//for canceled update
      if(tsm.getState(lname, ri) != TreeState.RESTART) {
        tree.setDisposed();
        tsm.setDisposed(lname, pname, tree, ri);
        tsm.setState(lname, pname, tree, ri, TreeState.HALTED);
      }

      DBLocalName name = tree.getMetaObject(META_LOCAL_NAME);
      Map<String, TreeDB> olddbs = null;
      String revKey = getRevKey(ri);
      if (name != null && name.name!=null) { 
        olddbs = dbs.get(name.name);
        if(olddbs!=null)
          olddbs.remove(revKey);
        
//        Map<String, Path> oldLoc = locationsP.get(name.name);
//        if(oldLoc!=null)
//          oldLoc.remove(revKey);
      }
      dbnm.forget(tree);
      
      if(cs!=null)
        cs.dispose();
    }
    return true;
  }
  @Override
  public void remove(String lname, String pname) {
    checkReloadState();
    Map<String, TreeDB> map = dbs.get(lname);
    TreeStateManager tsm = getTreeStateManager();
    RevisionInfo ri = null;
    TreeDB db = null;
    try {
      if(map!=null) {
        db = map.remove(NOREV);
        if (db != null){
          tsm = TreeStateManager.getOne(db.getTracker());
          ri = tsm.getRevisionInfo(db);
          String ris = ri==null?NOREV:ri.getRevision();
          if(ri!=null) 
            db = map.get(ris);
          stopTracking(db, ri, true);
        }
      }
    }finally {
      tsm.setDisposed(lname, pname, db, ri);
    }
    File f = new File(URI.create(path + "/" + lname));
    if (f.exists()) recDelete(f);
    // TODO look
  }


  @Override
  public String getLocation(Object proj) {
    File file = extractFile(proj);
    if(file!=null) {
      URI uri = file.toURI();//TODO test
      if(uri!=null)
        return uri.toString();
    }
    return null;
  }
  @Override
  public String getLocation(TreeDB treeDB, boolean noCache) {
    RevisionInfo ri = getTreeStateManager().getRevisionInfo(treeDB);
    String name = treeDB.getLocalName();
    if(name == null) {
      if(!noCache)
        return treeDB.cachedLocation;
      return null;//not related to current tracker?
    }
    java.nio.file.Path location = locationsP.getOrDefault(name, Collections.emptyMap())
                                    .get(getRevKey(ri));
    if(location == null){
      location = ppath.resolve(name);
      Map<String, Path> map = locationsP.get(name);
      if(map == null){
        map = new HashMap<>();
        locationsP.put(name, map);
      }
      map.put(getRevKey(ri), location);
    }
    return location.toUri().toString();
  }

  @Override
  public Map<String, Template> getReportTemplates(TreeDB db, TemplateScope ts) {
    URI tpath = getTemplPath();
    Map<String, Template> templs = null;
    if(tpath!=null){
      File templates = new File(tpath);
      if(templates.exists()){
        templs = new TreeMap<>();
        for(File f:templates.listFiles()){
          try{
            Template t = new Template(f.toURI().toString(), TemplateScope.BUILDIN);
            if(t!=null)
              templs.put(t.getId(), t);
          }catch(Exception e){
            //cannot load
          }
        }
      }
      if(templs == null || templs.isEmpty())
        return TemplatesManager.getLocalTemplates(db, ts);
      templs.putAll(TemplatesManager.getLocalTemplates(db, ts));
    }
    return templs;
  }
  @Override
  public Map<String, Group> getReportGroups(TreeDB db, TemplateScope ts) {
    URI tpath = getTemplPath();
    Map<String, Group> gr = null;
    if(path!=null){
      File templates = new File(getTemplPath());
      if(templates.exists()){
        gr = new TreeMap<>();
        for(File f:templates.listFiles()){
          try{
            Groups t = new Groups(f.toURI().toString(), TemplateScope.BUILDIN);
            if(t!=null)
              for(Group g:t.getAllGroups())
                gr.put(g.getId(), g);
          }catch(Exception e){
            //cannot load
          }
        }
      }
      return gr;
    }
    if(gr==null || gr.isEmpty())
      return TemplatesManager.getLocalGroup(db, ts);
    gr.putAll(TemplatesManager.getLocalGroup(db, ts));
    return gr;
  }

  private URI getTemplPath() {
    if(templpath!=null && !templpath.isEmpty())
      return URI.create(templpath);
    File templates = new File("./templates");
    try{
      if(templates.exists())
      return templates.toURI();
      templates = new File("../templates");
      if(templates.exists())
      return templates.toURI();
    }finally{
      if(templates.exists())
        templpath = templates.toURI().toString();
    }
    return null;
  }
  @Override
  public void removeTemplate(File to, TreeDB tree, TemplateScope ts) {
    //TODO implement    
  }

  @Override
  public File addReportTemplate(File from, TreeDB tree, TemplateScope ts) {
  //TODO implement
    return null;
  }
  @Override
  public void clearCache(TreeDB db, String localName, boolean delete) {
    super.clearCache(db, localName, delete);
    DBLocalName name = db.getMetaObject(META_LOCAL_NAME);
    if(name == null)
      name = DBLocalName.formLocalNameKey(db, localName, db.revision);
    Map<String, TreeDB> olddbs = null;
    String revKey = getRevKey(db.revision);
    if (name != null && name.name!=null) { 
      olddbs = dbs.get(name.name);
      if(olddbs!=null)
      {
        olddbs.remove(revKey);
//        if(!delete)
//          olddbs.put(revKey, ret);
      }
      Map<String, Path> oldLoc = locationsP.get(name.name);
      Path oldPath = oldLoc!=null?oldLoc.remove(revKey):null;
      if(!delete) {
//        if(ret!=null)
//          dblocalnames.put(ret, name);
//        if(olddbs!=null)
//          dbs.put(name, olddbs);
        String pname = dbnm.getProjectName(name);
        if(pname!=null && !pname.equals(db.getProjectName())){
          //TODO cannot happened here but needs to update cache
        }
      }else
        dbnm.forget(name);
    }
    // TODO Auto-generated method stub

  }
  
  private File extractFile(Object o) {
    if(o instanceof File)
      return (File)o;
    if(o instanceof URI)
      return new File((URI)o);
    return null;
  }

  @Override
  public OperationResult sourceSpecificTrackTreeDB(Object proj, String lname, String pname, boolean ignoreVersion,boolean noParallel,boolean check, boolean initCache, TreeDB db) {
    File ch = extractFile(proj);
    if(ch == null)
      return OperationResult.UNSUCEEDED;//no base file for loading
    URI uri = ch.getAbsoluteFile().toURI();//TODO test
    if(lname == null) {
      lname = ch.getName();
      if(lname == null)
        return OperationResult.UNSUCEEDED;
    }
    TreeStateManager tsm = getTreeStateManager();
    RevisionInfo ri = tsm.getRevisionInfo(db);
    TreeState ts = tsm.getState(lname, ri);
    if(ts!=TreeState.NOT_LOADED && ts!=TreeState.DISPOSED&& ts!=TreeState.HALTED&&ts!=TreeState.CANCELED&& ts!=TreeState.RESTART && ts!=TreeState.MODIFYING)//adds transaction
      return OperationResult.UNSUCEEDED;
    if(pname == null)
      pname = CoreUtils.getProjectName(ch);
    db.addTreeStateHooks(lname, pname);
    bindTreeToProject(proj, db, ri, lname, ch);
    registerNames(db, lname, pname);
//    if(dbs.containsKey(lname))
//      return false;//already loaded
    //cannot check tsm getState because filestracker also works on revisioned nodes
     // TreeDB db = TreeDB.load(lname, pname, tr, fs);
    try{
     // initClasses(db);
      //addStateHook(db);
      TreeStateManager.getOne(this).setPartiallyReady(lname, pname, db, ri);
      if(initCache)
        db.initCaches(lname, pname, ri, noParallel);
    }finally{
    }
    OperationResult op = OperationResult.SUCEEDED;
    if(getProjectLoadingState(uri, pname)!= ProjectLoadingState.VERSION_UPDATE)
      setProjectLoadingState(uri, pname, ProjectLoadingState.NOT_LOADING);
    return op;
  }
  @Override
  public void registerNames(TreeDB db, String lname, String pname) {
   DBLocalName lkey = DBLocalName.formLocalNameKey(db, lname, db.revision);
   db.registerMetaObject(META_LOCAL_NAME, lkey);
   dbnm.register(db,
       lkey,
       pname); 
  }
  @Override
  public void bindTreeToProject(Object proj, TreeDB db, RevisionInfo ri, String lname, File ch) {
    if(ch == null)
      ch = extractFile(proj);
    if(ri == null)
      ri = getTreeStateManager().getRevisionInfo(db);
    String ris = getRevKey(ri);
    Map<String, TreeDB> map = dbs.get(lname);
    if(map == null) {
      map = new HashMap<>();
      dbs.put(lname, map);
    }
    TreeDB old = map.put(ris, db);
    if(old!=null && db!=old) {
      treeCloseReaction(db);
    }
    Map<String, Path> map2 = locationsP.get(lname);
    if(map2 == null) {
      map2 = new HashMap<>();
      if(db == null)
        RLogger.logError("");
      locationsP.put(lname, map2);
    }
    map2.put(ris, ch.toPath());
  }
  @Override
  public void open(Object obj) {
    checkReloadState();
    File f = extractFile(obj);
    String lname = f.getName();
    Map<String, Path> locs = locationsP.getOrDefault(lname, Collections.emptyMap());
    TreeDB adb = null;
    Map<String, TreeDB> dbsa = dbs.get(lname);
    for(Entry<String, Path> en: locs.entrySet())
    {
      if(en.getValue().equals(f.toPath())) {  
         adb = dbsa != null ? dbsa.get(en.getKey()) : null; // VSCODE-PATCH: null guard (NPE when a project is opened during format conversion)
      }
    }
    if(adb == null && dbsa!=null)
      adb = dbsa.get(NOREV);
    if(adb!=null) {
      Path loc = locs.get(NOREV);
      if(loc!=null && loc.equals(f.toPath())) {
        notifyListeners(Moment.BEFORE, adb.getLocalName(),adb.getProjectName(),adb, TreeEvent.TREE_OPEN);//TODO move?
        notifyListeners(Moment.AFTER, adb.getLocalName(),adb.getProjectName(),adb, TreeEvent.TREE_OPEN);
      }
    }
  }

  @Override
  public void initClasses(TreeDB db) {
    //TODO move to API
    db.addNodeType(Comment.TYPE_NAME, Comment.class);
    db.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
    db.addNodeType(Document.TYPE_NAME, Document.class);
    db.addNodeType(ErrorNode.TYPE_NAME, ErrorNode.class);
    db.addNodeType(InstancesFolder.TYPE_NAME, InstancesFolder.class);
    db.addNodeType(Location.TYPE_NAME, Location.class);
    db.addNodeType(ReportData.TYPE_NAME, ReportData.class);
    db.addNodeType(ReportSettings.TYPE_NAME, ReportSettings.class);
    db.addNodeType(Requirement.TYPE_NAME, Requirement.class);
    db.addNodeType(RestrictedFolder.TYPE_NAME, RestrictedFolder.class);
    db.addNodeType(TestPurpose.TYPE_NAME, TestPurpose.class);
    db.addNodeType(TextNode.TYPE_NAME, TextNode.class);
    db.addNodeType(ToDoItem.TYPE_NAME, ToDoItem.class);
    db.addNodeType(VirtualNode.TYPE_NAME, VirtualNode.class);
  }

  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, boolean ignorev, boolean ignoreStorage, IResTracker tr, IProgressMonitor mon) throws IOException {
    return loadTreeDBDescription(path, ri, null, ignorev, ignoreStorage, tr, mon);
  }

  private TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, String name, boolean ignoreev,boolean ignoreStorage, IResTracker tr, IProgressMonitor mon) throws IOException {
    checkReloadState();
    File f = new File(path);
    if(!f.exists())
      throw new FileNotFoundException("Directory on path " +path+" is not exist");
    if(!f.isDirectory())
      throw new java.io.IOException("Object on path "+path+" is file instead of directory");
    File[] fs = f.listFiles(ff);
    if(fs.length==0)
      throw new FileNotFoundException(".project file is not found in given dir "+path);
    org.w3c.dom.Document doc = null;
    try {
      doc = loadInfo(fs[0]);
    } catch (SAXException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    if (doc!=null && !findNature(doc, "com.unitesk.requality.natures.reqcatalogue"))
      throw new RuntimeException("Project on given path is not a Requality project "+path);
    mon.beginTask("Load project description", 1);
    String pname = null;
    try{
      List<String> ln = Files.readAllLines(fs[0].toPath());
      pname = getNameFromInfo(doc);
    }finally{
      mon.doneTask();
    }
    RLogger.logDebug("get project call for "+path+" flag "+ignoreStorage );
    CacheStorage cs = getCacheStorage();
    CacheTransactionDescription msg = cs.startCacheTransaction("init caches for tree "+name, 2);
    try {
      TreeDB db = TreeDB.loadTreeInstance(fs[0].getParentFile().getName(),pname,ri, tr, new FSStorage(path, mon));
      initDBLoading(f, ri, db);
      return db;
    }finally {
      if(msg != null && msg.error == null)
        cs.commit(msg);
    }
  }
  @Override
  public TreeDB loadTreeDBDescription(URI path, RevisionInfo ri, String name, IProgressMonitor mon) throws IOException {
    return loadTreeDBDescription(path, ri, name, false, true, this, mon);
  }
  

  @Override
  public TreeDB loadTreeDBDescription(Object project,RevisionInfo ri, String name, IProgressMonitor mon) throws IOException {
  checkReloadState();
    if(project instanceof URI)
      return loadTreeDBDescription((URI)project, ri, name, mon);
    if(project instanceof File)
      return loadTreeDBDescription(((File)project).toURI(),ri, name, mon);
    return null;
  }

  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, boolean ignorev, boolean ignorestorage,
      IResTracker repoManager) throws IOException {
    checkReloadState();
    File f = extractFile(path);
    if(f == null)
      return null;
    //first search by path
    String ris = ri==null?NOREV:ri.getRevision();
    String name = f.getName();
    String revKey = getRevKey(ri);
    return dbs.getOrDefault(name, Collections.emptyMap()).get(revKey);
  }
  @Override
  public String getLocalName(URI path) {
    return new File(path).getName();
  }
  @Override
  public TreeDB getTreeDB(URI path, RevisionInfo ri, String name) throws IOException {
    checkReloadState();
    File f = extractFile(path);
    TreeStateManager tsm = getTreeStateManager();
    if(f == null)
      return null;
    //first search by path
    String ris = getRevKey(ri);
 
    Map<String, TreeDB> map2 = dbs.getOrDefault(name, Collections.emptyMap());
    TreeDB db = map2.get(ris);
    if(db!=null) {
      RevisionInfo ria = TreeStateManager.getRevisionInfo(db);
      if(ria!=null && !ria.getRevision().equals(ris))
        return null;
    }
    return (db == null || db.isDisposed())?null:db;
  }
  @Override
  public TreeDB getTreeDB(Object proj, RevisionInfo ri, String localname) throws IOException {
    checkReloadState();
    File f = extractFile(proj);
    if(f == null || !f.exists())
      return null;
    //first search by path
    String ris = getRevKey(ri);
    
    TreeDB db = dbs.getOrDefault(localname, Collections.emptyMap()).get(ris);
    return (db == null || db.isDisposed())?null:db;
  }
  
  @Override
  public void initDBLoading(Object proj, RevisionInfo ri, TreeDB db) {
    File f = extractFile(proj);
    if(f!=null)
      f.toPath().resolve(CoreUtils.CACHE_FOLDER_NAME).toFile().mkdirs();
  }

  @Override
  public CheckerContainer getCheckerContainer(TreeDB newDB, RevisionInfo ri) {
    return null;//null means default checkers
  }

  @Override
  public Path getRootLocation() {
    return ppath;
  }

  @Override
  public URI getGlobalCacheLocation() {
    return lppath;
  }

  @Override 
  public void close(TreeDB db){// TODO Auto-generated method stub
    if(db == null)
      return;
    notifyListeners(Moment.BEFORE, db.getLocalName(),db.getProjectName(), db, TreeEvent.TREE_CLOSE);
    getTreeStateManager().setState(db.getLocalName(), db.getProjectName(), db, TreeState.HALTED);
    DBLocalName lname =  db.getMetaObject(META_LOCAL_NAME);
   // locationsP.remove(lname.name);
    String s1 = null,s2 = null;
    for(Entry<String, Map<String, TreeDB>> en:dbs.entrySet()) {
      for(Entry<String, TreeDB> en2:en.getValue().entrySet())
        if(en2.getValue() == db) {
          s1 = en.getKey();
          s2 = en2.getKey();
          break;
        }
      if(s1!=null)
        break;
    }
    if(s1!=null)
      dbs.get(s1).remove(s2);
    notifyListeners(Moment.AFTER, db.getLocalName(),db.getProjectName(), db, TreeEvent.TREE_CLOSE);//TODO move?
  }
  
  @Override
  public void refresh(TreeDB project, TreeNode resource, int depth) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public Object getStoringEntity(TreeDB treeDB) {
    Map<String, Path> loc = locationsP.get(treeDB.getLocalName());//TODO local name?
    RevisionInfo ri = getTreeStateManager().getRevisionInfo(treeDB);
    String ris = getRevKey(ri);
    Path p = loc.get(ris);
    if(p!=null)
      return p.toFile();
    return null;
  }
  @Override
  public void refresh(Object storingEnt, int depth) throws Exception {
    // TODO Auto-generated method stub
    
  }
  
  @Override 
  public boolean addListener(Moment m, ITreeStateListener l){// TODO Auto-generated method stub
    return super.addListener(m, l) && tn.addListener(m, l);//TODO duplication?
  }
  
  @Override 
  public boolean removeListener(Moment m,ITreeStateListener l){// TODO Auto-generated method stub
    return super.removeListener(m, l) || tn.removeListener(m, l);//TODO duplication?
  }
  
  @Override 
  public synchronized void notifyListeners(Moment m, String lname, String pname,TreeDB db,TreeEvent type){// TODO Auto-generated method stub
    super.notifyListeners(m, lname, pname, db, type);
    tn.notifyListeners(m, lname,pname, db, type);
  }

  private java.nio.file.Path cpPath = null; 
  public synchronized CacheStorage getCacheStorage(){
    if(gcss == null)
    {
      try {
        initCaches();
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    return gcss;
  }
  public void initCaches() throws Exception{
    caches = new HashMap<>();
    Set<String> dbnames = CacheStorage.getCacheDBNames(false);
    for(String s: dbnames)
      if(s.equals(IAPICacheProvider.DEFAULT))
        gcss = new CacheStorage(s, false, cpPath,"crossproject","", null, null, null);
      else
        caches.put(s, new CacheStorage(s, false, cpPath,"crossproject_"+s,"", null, null, null));
  }

  public CacheStorage getCacheStorage(String id){
    if(caches == null)
      throw new RuntimeException("Caches are not inited!");
    return caches.get(id);
  }
  public CacheStorage getCPCacheStorage(TreeDB dbfrom) {
    return getCacheStorage();
  }
  public CPLinkResolver getCPLinkResolver(TreeDB dbfrom) {
    return getCMM(dbfrom).getLinkManipulator();
  }
  @Override
  public void disableListeners(byte b) {
    //TODO mb add
  }
  @Override
  public void enableListeners(byte b) {
    
  }

  @Override
  public boolean isDisabledListening(byte b) {
    return false;
  }
  public org.w3c.dom.Document loadDocByFolder(File file) {
    File[] fa = file.listFiles(ff);
    if (fa != null && fa.length == 1){
      try {
        org.w3c.dom.Document doc = loadInfo(fa[0]);
        if(findNature(doc, "com.unitesk.requality.natures.reqcatalogue"))
        {
          return doc;
        }
      } catch (IOException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      } catch (SAXException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
    }
    return null;
  }
//  @Override
//  public boolean isNewHandling(URI uri, String pname){//TODO add pname
//    if(uri!=null) {
//      Pair<TreeDB, String> p = getTree(uri);
//      if(p!=null) {
//        TreeDB db = p.getFirst();
//        if(db == null)
//          return false;
//        return tsm.getProjectLoadingState(this, p.getSecond(), tsm.getRevisionInfo(db));
//      }
//    }
//  return false;
//    
//  }
  private Pair<TreeDB,String> getTree(URI uri) {
    if(uri == null)//TODO test
      return null;
    checkReloadState();
    File f = new File(uri);
    String localName = f.getName();
    Map<String, Path> path = locationsP.get(localName);
    for(Entry<String, Path> en: path.entrySet())
      if(f.toPath().equals(en.getValue())) 
        return new Pair<TreeDB, String>(dbs.get(localName).get(en.getKey()), localName);
  return null;
  }
//
//  @Override
//  public void addNewHandling(URI uri, String pname){//TODO add pname
//  Pair<TreeDB, String> p = getTree(uri);
//  if(p!=null && p.getFirst()!=null)
//      tsm.markProjectLoading(this, p.getSecond(), TreeStateManager.getRevisionInfo(p.getFirst()));
//    
//  }
//  @Override
//  public void removeNewHandling(URI uri, String lname, String pname){//TODO add pname
//    Pair<TreeDB, String> p = getTree(uri);
//    if(p!=null && p.getFirst()!=null)
//      tsm.resetProjectLoadingState(this, p.getSecond(), TreeStateManager.getRevisionInfo(p.getFirst()));
//  }
  @Override
  public Object getProjectEntity(String lname, String pname, boolean onlyDB) {
    checkReloadState();
    if(lname!=null)
    {
      Map<String, TreeDB> dbs2 = dbs.get(lname);
      if(dbs2!=null) {
        TreeDB db = dbs2.get(NOREV);
        if(db!=null)
            return db;
      }
    }
    if(pname!=null) {
      Set<DBLocalName> nms = dbnm.getLocalNames(pname);
      if(nms!=null && nms.size() == 1) {
        DBLocalName s = nms.iterator().next();
        return dbs.get(s.name);
      }
    }
    return null;
  }
  @Override
  public String getJSResourcesPath() {
    File jp = new File("./jstools");
    if(!jp.exists())
      jp = new File("../jstools");
    try {
    return jp.getCanonicalPath();
  } catch (IOException e) {
    RLogger.logError(e);
  }
    return null;
  }
  @Override
  public String getLocalName(Object proj) {
    File f = extractFile(proj);
    if(f!=null)
      return getLocalName(f.toURI());
    return null;
  }
    @Override
    public boolean isOpen(Object project) {
      File f = extractFile(project);
      if(f!=null)
        return true;//TODO check
      return false;
    }
    @Override
    public void close(Object project) {
    // TODO Auto-generated method stub
    
    }
    @Override
    public void markNonLoadable(URI uri) {
    // TODO Add method to add non-loadable nature to file
    
    }

    @Override
    public void setGUISettings(boolean noGUI, boolean alwaysOkOnNoGui) {
      this.noGUI = noGUI;
      alwaysOK = alwaysOkOnNoGui;
    }
    @Override
    public Collection<File> getAllResourceProjects() {
      File root = ppath.toFile();
      if(!root.exists()) {
        RLogger.logError("incorrectly inited file tracker");
        return Collections.emptySet();//incorrectly inited tracker
      }
      return Arrays.asList(root.listFiles(new FileFilter() {
        @Override
        public boolean accept(File f) {
          return f.isDirectory();
        }
      }));
  }
  public File changeProjectName(String lname, String pnameold, String pnamenew) {
    Map<String, TreeDB> m = dbs.getOrDefault(lname, Collections.emptyMap());
    TreeDB db = m.get(pnameold);
    if(db!=null) {
      m.put(pnamenew, db);
      return new File(URI.create(getLocation(db, false)));
    }
    return null;
  }
  @Override
  public Pair<Boolean, TreeDB> handleProjectRename(Path ppath, TreeDB db, String localName,
        String projectNameOld, String projectNameNew, boolean ignoreMessages,
        IProgressMonitor mon) {
    //TODO test or call more proper method
    Set<String> names = new HashSet<>();
    if(ppath == null)
      ppath = new File(URI.create(getLocation(db, false))).toPath();
    File parFile = ppath.toFile();
    CoreUtils.forgetName(parFile);
    dbnm.forget(db);
    if(projectNameNew == null)
      projectNameNew = db.getProjectName();
    if(projectNameNew.equals(projectNameOld)) {
      return new Pair<>(false, db);
    }
    Map<String, Set<TreeDB>> relates = new HashMap<>();
    names.add(projectNameNew);
    names.add(projectNameOld);
    RelationsTreeContext s = CoreUtils.gatherRelatedTrees(db, null, 1, names, false);
    Set<TreeDB> dbs = new HashSet<>(s.allRelated);
    //dbs.remove(db);
    relates.put(localName, dbs);//TODO more detailed related use
    //TODO remove relates?
    //DBNamesManager.DEFAULT.forget(localName);//TODO mb fork
    //IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(localName);//TODO rpoj name?
    String name = getProjectName(db);
    if(name!=null)
      DBNamesManager.DEFAULT.register(
        DBLocalName.formLocalNameKey(db, localName,db.revision), name);
    if(name == null || !projectNameOld.equals(projectNameNew)) {
      File nf = changeProjectName(localName, projectNameOld, projectNameNew);
      dbnm.forget(db);
      dbnm.register(db, 
        DBLocalName.formLocalNameKey(db, localName, db.revision),
        projectNameNew);
      return new Pair<>(true, db);
    }
    return new Pair<>(false, db);
  }
  @Override
  public void handleNewProjects(List<URI> reqProjects) {
    //TODO implement, for now used only by Activator and related loading
  }
}
