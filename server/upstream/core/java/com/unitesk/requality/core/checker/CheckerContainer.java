package com.unitesk.requality.core.checker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IStoppable;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.ReferenceOutdateMark;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.NullProgressMonitor;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.ProjectRemovedWait;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.ErrorNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class CheckerContainer {
  public static long CHECK_INTERVAL = 2000;
  private static long NEXT_TREE_CHECK_TIMEOUT = 120000;
  public static String VATTR_ERRORS = "_virtualErrors";
  protected boolean enabled = true;
  private ArrayList<INodeChecker> checkers = new ArrayList<>();
  private boolean runTreeCheckers = false;
  protected LinkedHashSet<UUID> nodesTo = new LinkedHashSet <>();
  protected ConcurrentHashMap<UUID,TreeNode> lastNodesMap = new ConcurrentHashMap<>();
  protected Iterator<UUID>nodesToIt = nodesTo.iterator();
  protected TreeDB curDB;
  protected String curDBName, curDBLocalName;//TODO rename?
  private static ChangesSet<Attribute> CHANGE_SET;

  static{
    List<String>s = new LinkedList<>();
    s.add(VATTR_ERRORS);
    Map<String, Attribute> am = new HashMap<>();
    am.put(VATTR_ERRORS, null);
    CHANGE_SET = new ChangesSet<>(
        Collections.emptySet(), 
        new HashSet<>(s),
        Collections.emptySet(),
        am, 
        am,
        s);
  }

  
  private ITreeStateListener tsl = new ITreeStateListener() {
    @Override
    public void open(TreeDB db) {
    }

    @Override
    public void close(TreeDB db) {
      if(db==curDB) 
        delete(db.getLocalName(), db.getProjectName(), db);
    }

    @Override
    public void delete(String lname, String pname, TreeDB db) {
      if(db==curDB) {
        stopOldThread(true);
        nodesTo.clear();
        lastNodesMap.clear();
        nodesToIt = nodesTo.iterator();
      }
    }

    @Override
    public void settingsChanged(TreeDB db) {
    }
  };

  private DefaultTreeChangeListener dcl;

  public static final CheckerContainer DISABLED_CHECKER_CONTAINER = new CheckerContainer(null) {
    @Override
    public boolean isEnabled() {
      return false;
    }
  };
  public static CheckerContainer getOne(TreeDB treeDB, RevisionInfo ri) {
    String id = CoreUtils.getTreeUniqueId(treeDB, ri);
    CheckerContainer ret = null;
    ret = treeDB.getMetaObject(META_CONTAINER_NAME);//TODO possible container duplication?
    if(ret == null) {
      synchronized(setHooks) {
        ret = treeDB.getMetaObject(META_CONTAINER_NAME);
        if(ret == null) {
          ret = new CheckerContainer(treeDB);
          treeDB.registerMetaObject(META_CONTAINER_NAME, ret);
        }
      }
    }
    return ret;
//    synchronized (cmap) {
//      if(cmap.containsKey(id)) {
//        ret = cmap.get(id);
//        ret.stopOldThread();
//        ret.setTreeDB(null);//stop it
//      }
//      ret = new CheckerContainer(treeDB);
//      cmap.put(id, ret);
//    }
  }
  public static String META_CONTAINER_NAME="CHECKER_CONTAINER";
  private static Set<TreeStateManager> setHooks = new HashSet<>();
  protected CheckerContainer(TreeDB db){
    curDB = db;
    if(curDB!=null) {
      curDBName = curDB.getVisibleName();
      curDBLocalName = curDB.getSemiLocalName();
    }else
      curDBName = "Cannot load name of empty tree!";
    dcl = new DefaultTreeChangeListener(){
      @Override
      public void changed(TreeNode node, ChangesSet<Attribute> what) {
        if(what.contains(VATTR_ERRORS)){
          virtualErrorsChanged(node);
          if(what.getAll().size()>1&&(!what.contains(ReferenceOutdateMark.WARNING_KEY)||what.getAll().size()>2))
            addToCheckQ(node, true);
        }else
          if(!what.contains(ReferenceOutdateMark.WARNING_KEY)||what.getAll().size()>1)
            addToCheckQ(node, true);
      }
    };
    if(db!=null)
      db.addGUIListener(dcl);
  }
  public void setTreeDB(TreeDB db) {
    if(curDB!=null) {
      curDB.getTracker().removeListener(Moment.BEFORE, tsl);
      curDB.removeGUIListener(dcl);
      if(hook!=null)
        curDB.getTracker().getTreeStateManager().removeStateHook(curDBLocalName, tss, hook);
    }
    curDB = db;
    stopOldThread(false);
    if(curDB!=null) {
      curDBName = curDB.getVisibleName();
      curDBLocalName = curDB.getSemiLocalName();
      curDB.getTracker().addListener(Moment.BEFORE, tsl);
      dcl = new DefaultTreeChangeListener(){
        @Override
        public void changed(TreeNode node, ChangesSet<Attribute> what) {
          if(what.contains(VATTR_ERRORS)){
            virtualErrorsChanged(node);
            if(what.getAll().size()>1&&(!what.contains(ReferenceOutdateMark.WARNING_KEY)||what.getAll().size()>2))
              addToCheckQ(node, true);
          }else
            if(!what.contains(ReferenceOutdateMark.WARNING_KEY)||what.getAll().size()>1)
              addToCheckQ(node, true);
        }
      };
      db.addGUIListener(dcl);
      runCheckers();
    }
  }
  
  public void virtualErrorsChanged(TreeNode node){
    
  }
  private Map<String, IMark[]> old = new HashMap<>();
  public void checkSingleNode(Map<UUID, TreeNode> map, LinkedList<UUID>q, boolean late) {
    UUID node = null;
    boolean isEm = q==null || q.isEmpty();
    node = isEm?null:q.getFirst();
    boolean rem = false;
    if(q!=null){
      q.removeFirst();
      rem = true;
    }
    if(rem && node!=null){
      synchronized(nodesTo){
        nodesTo.remove(node);
        nodesToIt = nodesTo.iterator();
      }
    }
    if(node == null)
      return;
    TreeNode treenode = map.get(node);
    if(treenode == null)
      return;
    try{
      if(!treenode.isDisposed()&&!treenode.getTreeDB().isDisposed()){
        IMark[]oldp = old.getOrDefault(treenode.getSUUId(), new IMark[0]);
        int hc = oldp.hashCode();
        treenode.updateCheckerProblems();
        IMark[] newp = treenode.getCheckerProblems();
        if(newp == null || newp.hashCode()!=hc){
          if(!Arrays.equals(oldp,newp)){
            treenode.getTreeDB().fireNodeGUIChange(treenode, CHANGE_SET, OFlag.NONE, Moment.BEFORE);

            old.put(treenode.getSUUId(), newp);
            
            treenode.getTreeDB().fireNodeGUIChange(treenode, CHANGE_SET, OFlag.NONE, Moment.AFTER);
          }
        }
      }
    }
    catch(Exception e)
    {
      RLogger.logError(e);
    }
    finally{
      treenode.clearChecked();
    }
  }


  public IMark[] gatherProblems(TreeNode node) {
    if (node.isDisposed() || !enabled) // deleted
      return null;
    //curNode = node;
    ArrayList<IMark> result = new ArrayList<>(); 
    try{
      if (node instanceof ErrorNode) {
        result.add(new CheckerProblem(node, "problem_corrupted_element", "Corrupted element %s", new String[] {node.getQualifiedId()} ));
      }
      if(!node.isVirtual()){
        node.waitLoaded();
        
        for (INodeChecker checker : new ArrayList<>(checkers))
          checker.check(node, result);
      }
//      for(IMark m:node.getTreeCheckerProblems())
//        result.add(m);
      applyProblemsInfo(node, result);
    }finally{
      //node.getTreeDB().stopChangeRow(); 
    }
    return result.toArray(new IMark[0]);
  }

  public TreeDB getDB() {
    return curDB;
  }

  protected void applyProblemsInfo(TreeNode node, ArrayList<IMark> result) {
    for (IMark problem : result) {
      RLogger.logDebug(node.getUserVisibleName()+": "+problem,5);
    }
  }

  public void addChecker(INodeChecker checker) {
    //TODO checker loadings for non-eclipse execution
    checkers.add(checker);
  }
  
  public boolean isEnabled() {
    if(enabled && curDB != null && !curDB.isDisposed())
    {
      IResTracker tr = curDB.getTracker();
      TreeStateManager tsm = TreeStateManager.getOne(curDB.getTracker());
      if(tsm!=null && tsm.areCachesInited(tr, curDB.getLocalName(), TreeStateManager.getRevisionInfo(curDB))) {
      RevisionInfo ri = TreeStateManager.getRevisionInfo(curDB);
      return tsm!=null?tsm.getState(curDB.getLocalName(), ri)==TreeState.READY:false;
      }
    }
    return false;
  }

  
  public void forceCheckQ(boolean runOnlyPart) {
    stopOldThread(false);
    runCheckerThreadActions(runOnlyPart, NullProgressMonitor.ONE);//TODO implement?
    startNewThread();
  }

  public void addToCheckQ(TreeNode node, boolean afterChange) {
    if(checkthread == null && isEnabled())
      synchronized (CheckerContainer.this) {
         if(checkthread == null && isEnabled())
           startNewThread();
      }
    synchronized (nodesTo) {      
      if(!node.canBeCheckedIn(this))//marked to be checked by another container
        return;
      if(afterChange)
        nextTreeCheck = -1;
      node.markToBeChecked(this);
      UUID nodeuid = node.getUUId(); 
      nodesTo.remove(nodeuid);
      nodesTo.add(nodeuid);
      nodesToIt = nodesTo.iterator();
      lastNodesMap.put(nodeuid, node);
    }
    synchronized (CheckerContainer.this) {
      CheckerContainer.this.notifyAll(); 
    }
  }

  public void addAllLoadedToQ() {
    if(curDB.revision!=null && curDB.revision!=RevisionInfo.NO_REV)
      return;
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(curDB.getRootNode(),false,false,true,true,true);
    Iterator<TreeNode> it = dftw.iterator();
    while(it.hasNext()){
      TreeNode node = it.next();
      addToCheckQ(node, false);
    }
  }
  private static TreeState[]tss = new TreeState[] {TreeState.HALTED, TreeState.READY,  TreeState.CANCELED};
  private IStateChangeHandler hook = null;
  public void runCheckers() {
    stopOldThread(false);
    TreeStateManager tsm = curDB.getTracker().getTreeStateManager();
    String lname = curDB.getLocalName();
    if(tsm.isFullReady(lname, tsm.getRevisionInfo(curDB))) {
      addAllLoadedToQ();
      startNewThread();
    }else {
      AtomicReference<TreeDB> fdb = new AtomicReference<>(curDB);
      hook = new AAllTreeStateChangeHandler() {
        boolean removed = false;
        private void removeIfNeeded(String lname) {
          if(!removed) {
            removed = true;
            tsm.removeStateHook(lname, tss, this);
          }
        }
        @Override
        public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
            TreeState oldState, TreeState state) {
          TreeDB cand = ResManager.getManager().getTreeDB(db, lname, pname, false, null);
          boolean instanceChanged = fdb.get() != cand;
          if(instanceChanged) {
            removeIfNeeded(lname);
            if(checkthread!=null)
              stopOldThread(true);
            return;
          }
          if(state == TreeState.CANCELED) {
              removeIfNeeded(lname);
              if(checkthread!=null)
                stopOldThread(true);
          }else if(state == TreeState.HALTED) {
            ProjectRemovedWait.registerWaitHalted(tsm, db, lname, ri, TreeState.LOADED_NO_CACHE, new Runnable() {
              @Override
              public void run() {
                removeIfNeeded(lname);
                if(checkthread!=null)
                  stopOldThread(true);
              }
            });
          }
          if(state == TreeState.READY) {
            if(checkthread!=null)
              stopOldThread(false);
            addAllLoadedToQ();
            removeIfNeeded(lname);
            startNewThread();
          }
        }
      };
      tsm.addStateHook(lname, tss, hook);
    }
  }
  private long nextTreeCheck = -1;
  protected boolean runCheckerThreadActions(boolean late, IProgressMonitor coreMon){
    TreeState ts = null;
    try{
      if(late){
        TreeDB db = curDB;
        if(!isEnabled())
        {
          return false;
        }
      } 
      if(coreMon.isCanceled())
        return false;
      LinkedList<UUID> lbq = null;
      synchronized (nodesTo) {
        lbq = new LinkedList<>(nodesTo);
      }
      int i = Math.max(lbq.size()/10,500);
      boolean checked = false;
      while(!lbq.isEmpty() && (!late||i>0)){
        if(late)
          i--;
        if(coreMon.isCanceled())
          return false;
        checkSingleNode(lastNodesMap,lbq,late);
        checked = true;
      }
      if(runTreeCheckers && lbq.isEmpty() && checked) {
        if(nextTreeCheck != -1 && System.currentTimeMillis()<nextTreeCheck) 
          return true;
        if(coreMon.isCanceled())
          return false;
        nextTreeCheck = System.currentTimeMillis() + NEXT_TREE_CHECK_TIMEOUT; 
        DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(curDB.getNode(Requirement.getTypeRootQId()));
        Iterator<TreeNode> it = dftw.iterator();
        while(it.hasNext()){
          TreeNode node = it.next();
          ReentrantLock rl = node.getTreeCheckersLock();
          if(coreMon.isCanceled())
            return false;
          try {
            rl.lock();
            node.clearTreeCheckerProblems();
          }finally {
            rl.unlock();
          }
        }
        for(ITreeChecker checker:API.getTreeCheckers(curDB))
          try {
            checker.check(curDB);
          }catch(Exception e) {
            RLogger.logError(e);
          }
      }
    }
    finally{

    }
    return true;
  }
  public long checkthreadid = -1;
  public Object checkThreadStarted = null;
  public Object checkthread = null;//TODO replace by runnable with monitor?
  public IStoppable checkStopper = null;
  //TODO test it...
  protected void startNewThread() {
    Thread t = null;
    IStoppable st = null;
    if(checkthreadid != -1)
      stopOldThread(false);
    if(curDB.isDisposed())
      return;
    synchronized (CheckerContainer.this) {
      checkthread = t = new Thread(new Runnable(){
        @Override
        public void run() {
          IStoppable st = null; 
          try {
            Thread pc = null;
            synchronized (CheckerContainer.this) {
              if(!(checkthread instanceof Thread)||checkthread!=this)
                return;
              pc = (Thread)checkthread;
              st = checkStopper;
            }
            final Thread c = pc;
            boolean canRun = CoreUtils.getWorkspaceOperation().isEmpty();
  
            boolean emp = true;
            synchronized (nodesTo) {
              emp = nodesTo.isEmpty();
            }
            while(checkthreadid == c.getId() && !emp && 
                !curDB.isDisposed() && !curDB.getStorage().getMonitor().isCanceled()){
              canRun = CoreUtils.getWorkspaceOperation().isEmpty();
              //have workspace ops
              if(canRun){
                synchronized (nodesTo) {
                  emp = nodesTo.isEmpty();
                }
                canRun = (!emp && isEnabled());//no nodes or empty
              }
              if(canRun&&checkthreadid == c.getId())
                runCheckerThreadActions(true, NullProgressMonitor.ONE);//TODO replace by progress monitor
              synchronized (nodesTo) {
                emp = nodesTo.isEmpty();
              }
              canRun = (!emp && isEnabled());//no nodes or empty
              if(!emp && checkthreadid == c.getId()){
                try {
                  synchronized (CheckerContainer.this) {
                    CheckerContainer.this.notifyAll(); 
                    CheckerContainer.this.wait(CHECK_INTERVAL);
                  }
                } catch (InterruptedException e) {
                //  RLogger.logError(e);
                }
              }
              synchronized (nodesTo) {
                emp = nodesTo.isEmpty();
              }
              canRun = (!emp && isEnabled());//no nodes or empty
            }
            synchronized (CheckerContainer.this) {
              if(checkthreadid == c.getId()){
                checkthread = null;
                checkStopper = null;
                checkthreadid = -1;
              }
            }
          }finally {
            Global.unRegisterStoppable(st);
          }
        }
      },"Checking nodes for "+curDBName);
      final Thread ft = t;
      checkStopper = st = new IStoppable(){
        @Override
        public void stop() {
          synchronized (CheckerContainer.this) {
            try {
              if(checkthread!=null && checkthread instanceof Thread && checkthread == ft){
                resetCheckerThreadVariables();
              }
            }finally {
              Global.unRegisterStoppable(this);
            }
          }
        }
      };
     // t = (Thread)checkthread;
      checkthreadid = t.getId();
      Global.registerStoppable(st);
    }
    if(checkThreadStarted == null) {
      checkThreadStarted = checkthread;
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }
  }

  protected void resetCheckerThreadVariables() {
    synchronized (this) {
      checkthread = null;
      checkthreadid = -1;
      if(checkStopper!=null)
        Global.unRegisterStoppable(checkStopper);
      checkStopper = null;
      checkThreadStarted = null;
      nextTreeCheck = -1;
    }
  }
  public void stopOldThread(boolean halted) {
/*    тут надо логику посмотреть и возможно переписать, явно путаются понятия потока
    обработки проверок и контейнера(последний переиспользуется)
    или нужно пересоздать контейнер иногда*/
    Object ct = null;
    IStoppable s = null;
    long id = -1;
    synchronized (this) {
      ct = checkthread;
      s = checkStopper;
      id = checkthreadid;
    }
    Thread t = null;
    if(ct!=null && ct instanceof Thread){
      //Global.unRegisterStoppable(s);
      synchronized(ct){
        try{
          ct.notify();
        }catch(IllegalMonitorStateException ir){
          ir.printStackTrace();
        }
      }
      synchronized (ct) {
        ((Thread)ct).interrupt();
      }
      synchronized (this) {
        resetCheckerThreadVariables();
        Global.unRegisterStoppable(s);
      }
    }
  }
  public void registerProblems(TreeNode treeNode, IMark[] checkerProblems) {
    treeNode.getTreeDB().addWarnings(treeNode, checkerProblems == null?null:new CopyOnWriteArrayList<IMark>(checkerProblems));//TODO test
  }
  public static CheckerContainer init(IResTracker tracker, TreeDB newDB, RevisionInfo ri) {
    CheckerContainer cc = tracker.getCheckerContainer(newDB, ri);
    if(cc == null)
      return newDB.getCheckerContainer(ri);
    return cc;
  }
  public void markRunTreeCheck() {
    runTreeCheckers = true;
  }
  public void attachGuiListener(TreeDB db) {
    if(db == curDB)
      db.addGUIListener(dcl);
    else
      RLogger.logDebug("Attempt to add listener to non-actual tree on "+this.toString()+" ("+
         (db!=null?db.toString():"null")+"->"+(curDB==null?"null":curDB.toString())+")");
  }
}
