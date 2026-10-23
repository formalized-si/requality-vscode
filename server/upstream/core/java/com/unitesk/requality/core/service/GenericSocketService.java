package com.unitesk.requality.core.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.events.TreeNodeChangeEvent.TreeNodeChangeEventType;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.document.BrowserState;
import com.unitesk.requality.document.IDocumentSocketServiceStateListener;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;



public abstract class GenericSocketService implements ISocketStateListener{
  private static final int TIMER_TO_DISPOSE = 30000, START_DISPOSE_TIMER = 120000, WAIT_INTERVAL = 1000;
  private static final TreeState[] LISTENED_STATES = new TreeState[] {TreeState.HALTED, TreeState.MODIFYING, TreeState.READY};
  public Map<String,BrowserState> states = new HashMap<>();
  protected TreeDB db;
  private boolean viewDisconnected = false;
  protected ReadWriteLock listLock = new ReentrantReadWriteLock();
  protected TreeNode startNode;
  protected FSId fsid;
  protected List<IDocumentSocketServiceStateListener> listeners = new LinkedList<>();
  protected boolean isDisposed = false;
  protected ConcurrentLinkedQueue<WSSocket> currentActiveSockets = new ConcurrentLinkedQueue<>();
  protected ConcurrentLinkedQueue<WSSocket> currentInitedSockets = new ConcurrentLinkedQueue<>();

  public Map<TreeNode, List<WSSocket>> resourceNodeToSocks = new HashMap<>();//node -> set of session in children
  protected Map<TreeNode, TreeNode> sateliteNodeParent = new HashMap<>();
  
  private ILinkedStateProvider linked = null;
  private List<ISocketMessageListener> moreMsgHandlers = new LinkedList<>();
  private ISocketMessageListener mesListener = null,
                                 coListener = null;
 
  Set<TreeDB> listenedTrees = new HashSet<>();
  Set<IResTracker> listenedTrackers = new HashSet<>();
  //protected Runnable actionsOnInit;
  public boolean isDisposed() {
    if(isDisposed)
      return true;
    WSServer server = getWSProvider(true);
    if(server!=null)
      return server.isDisposed(getFSIDFull());
    return false;//or true?
  }
  public Integer getPort() {
    return getPort(false);
  }
  public Integer getPort(boolean ignoreDispose) {
    WSServer s = getWSProvider(ignoreDispose);
    return s!=null?s.getPort():0;
  }
  public void registerMessangeHandler(ISocketMessageListener ml) {
    moreMsgHandlers.add(ml);
  }
//  public WSSocket getSocket() {
//  return getSocket(true);  
//  }
  WSServer getWSProvider(boolean ignoreDispose) {
    if(isDisposed && !ignoreDispose)
      return null;
    return wsprovider;
  }

  WSServer getWSProvider() {
    return getWSProvider(false);
  }
  
  private WSServer wsprovider = null;
  private ITreeChangeListener disposedListener;
  
  void initServer(GenericSocketService gss, String group, String id) {
    if(wsprovider != null)
      throw new RuntimeException("Attempts to reinit alreay inited socket service");
    WSServer wss;
    try {
      wss = WSServer.getOne(group, id);
      if(wss == null)
        return;
      while((wss.getServer() == null || wss.getServer().isStarting())&&
          !(wss.getServer().isFailed()||wss.getServer().isStopped()||wss.getServer().isStarting()))
        try {
          Thread.currentThread().join(200);
        } catch (InterruptedException e) {
          //  TODO Auto-generated catch block
          e.printStackTrace();
        }
      wsprovider = wss;
    } catch (PortOccupiedException e1) {
      RLogger.logError(e1);
    }
    //last part was in init on message 
  }
  public boolean hasRelatedSessions(TreeNode node) {
    if(node == null)
      return false;
    List<WSSocket>sess = null;
    synchronized(resourceNodeToSocks) {
      sess = resourceNodeToSocks.get(node);
      if(sess != null && sess.size()>0)
         return true;
    }
    TreeNode par = sateliteNodeParent.get(node);
    if(par!=node)
      return hasRelatedSessions(par);
    return false;
  }
  public void collectRelatedSessions(TreeNode node, AtomicReference<Collection<WSSocket>> ret){
    if(node == null)
      return;
    List<WSSocket>sess;
    synchronized (resourceNodeToSocks) {
      sess = resourceNodeToSocks.get(node);
      if(sess!=null && sess.size()>0) {
        Collection<WSSocket> csel = ret.get();
        if(csel == null) {
          csel = new HashSet<WSSocket>();
          ret.set(new LinkedList<>(sess));
        }else
          csel.addAll(sess);
      }
    }
    TreeNode par = sateliteNodeParent.get(node);
    if(par!=node)
      collectRelatedSessions(par, ret);
  }
    
  public Collection<WSSocket> getRelatedSessions(TreeNode node){
    AtomicReference<Collection<WSSocket>> ret = new AtomicReference<>();
    collectRelatedSessions(node, ret);
    Collection<WSSocket> cres = ret.get();
    return cres != null ? cres : Collections.emptySet();
  }

  protected void updateListeners() {
    ITreeChangeListener guilist = getGUITreeChangeListener(),
        list = getTreeChangeListener(), 
        glist = getGenericTreeChangeListener();
    if(guilist!=null && !db.getGUIListeners().contains(guilist))
      db.addGUIListener(guilist);
    if(list!=null && !db.getListeners().contains(list))
      db.addListener(list);
    if(glist!=null && !db.getListeners().contains(glist))
      db.addListener(glist);
    if(!db.getListeners().contains(disposedListener))
      db.addListener(disposedListener);
    if(list!=null || guilist!=null || glist!=null)
      if(!listenedTrees.contains(db))
        listenedTrees.add(db);
      
  }
  public abstract boolean isReadOnlyOperation (String op);
  public GenericSocketService(TreeNode startNode, String socketGroup, FSId fsid, ILinkedStateProvider prov) {
    super();
    RLogger.logDebug("new instnce (paused? "+disposePaused+") on "+socketGroup+" "+fsid+" "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()),6);
    this.fsid = fsid;
    this.db = startNode.getTreeDB();
    this.startNode = startNode;
    WSServer wsserv = getWSProvider();
    if(wsserv == null) { 
      initServer(this, socketGroup, getFSIDFull());
      wsserv = getWSProvider();
    }
    mesListener = getMessageListener();
    coListener = new ISocketMessageListener() {
      @Override
      public boolean handleMessage(WSSocket ws, Session session, String op, String win, JSONObject obj) {
        TreeStateManager tsm = db.getTracker().getTreeStateManager();
        RevisionInfo ri = TreeStateManager.getRevisionInfo(db);
        if(db.isDisposed()) {
          dispose(true);
          return false;
        }
        TreeState state = tsm.getState(db.getLocalName(), ri);
        //TODO track transaction state for treedb and translate it into status
        //TODO proper responce on non-readonly operations with transactions
        if(state!=TreeState.READY && state!= TreeState.MODIFYING) {
          tsm.waitState(db.getLocalName(), ri, new TreeState[] {TreeState.READY, TreeState.HALTED}, 5000);//wait for 5 sec for loading state
          state = tsm.getState(db.getLocalName(), ri);
          if(state != TreeState.MODIFYING) {
            tsm.waitState(db.getLocalName(), ri, new TreeState[] {TreeState.READY, TreeState.HALTED}, 295000);//wait for 5 minutes on non-transaction project
            state = tsm.getState(db.getLocalName(), ri);
          }else if(!isReadOnlyOperation(op)) {
            ws.send(session, "readOnlyModeMessage", new Pair<>("message", db.getVisibleName()));
            return true;
          }
            //else long transaction
        }
        if(state != TreeState.READY && state != TreeState.MODIFYING) {
          RLogger.logError("Socket "+this.getClass().getSimpleName()+" waits for "+db.getSemiLocalName()+" to be ready for 5 minutes but failed");
          return false; 
          //cannot handle because of incorrect state of tree, then we need to stop request by timeout
        }
        boolean ret = mesListener==null?false:mesListener.handleMessage(ws, session, op, win, obj);
        if(ret)
          return ret;
        if(moreMsgHandlers!=null)
          for(ISocketMessageListener msh:moreMsgHandlers) 
            if(msh.handleMessage(ws, session, op, win, obj))
              return true;//multiply handling is not a healthy case
        return false;
      }

      @Override
      public String getId() {
        return getFSIDFull();
      }
    };
  
    if(coListener!=null && wsserv!=null) 
      wsserv.registerMessageAndStateListeners(coListener, this);
    disposedListener = new ITreeChangeListener() {
        
        @Override
        public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
          // TODO Auto-generated method stub
          
        }
        
        @Override
        public void deleted(TreeNode nodeDesc, byte flag, Moment m) {
          if(m == Moment.BEFORE && wsprovider != null) {
            if(nodeDesc.getSUUId().equals(startNode.getSUUId()) ||
                hasRelatedSessions(nodeDesc)) {
              Collection<WSSocket> sess = getRelatedSessions(nodeDesc);
              wsprovider.sendCloseMessage(sess, "Node is removed "+nodeDesc.getSUUId(), true);
              updateDisposeTimer(TIMER_TO_DISPOSE, false);
            }
          }
        }
        
        @Override
        public void created(TreeNode node, byte flag) {
          // TODO Auto-generated method stub
          
        }
        
        @Override
        public void childrenOrderChanged(TreeNode node, byte flag) {
          // TODO Auto-generated method stub
          
        }
        
        @Override
        public void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
          // TODO Auto-generated method stub
          
        }
        
        @Override
        public void changed(List<TreeNodeChangeEvent> changes) {
          for(TreeNodeChangeEvent ce: changes){
            if(ce.getType() == TreeNodeChangeEventType.DELETE && ce.getMoment() == Moment.BEFORE) 
              deleted(ce.getTreeNodeTarget(), ce.getFlag(), ce.getMoment()); 
          }
        }
        
        @Override
        public void beforeCreated(NodeDesc node, byte flag) {
          // TODO Auto-generated method stub
          
        }
    };
    if(!db.getListeners().contains(disposedListener))
      db.addListener(disposedListener);
    linked = prov;
    registerSocket(this);
    updateDisposeTimer(START_DISPOSE_TIMER, true);//stop service if no connection
    generateSess();
    IResTracker tracker = db.getTracker();
    if(listenedTrackers.add(tracker)) {
      tracker.addListener(Moment.BEFORE, tsl);
      tracker.getTreeStateManager().addStateHook("*", LISTENED_STATES, treeStateListener);
    }
  ITreeChangeListener genlist = getGenericTreeChangeListener();
    if(genlist!=null) {
      if(!db.getListeners().contains(genlist)) {
        db.addListener(genlist);
        if(!listenedTrees.contains(db))
          listenedTrees.add(db);
      }
    }
    updateListeners();
  }
  private String extId = null;
  private static Long id = 0L;
  public String getExternalId() {
    if(extId == null)
      extId = "External_"+(id++);
    return extId;
  }
  
  public String getFSIDFull() {
    if(fsid!=null)
      return fsid.getFullIdString();
    return getExternalId();
  }
  public FSId getFSId() {
    return fsid;
  }

  public void addStatusListener(IDocumentSocketServiceStateListener sl) {
    JobManager.jm.startJob("add status listener to socket "+getFSIDFull(), new RunnableWithMonitor() {
      
      @Override
      public void run(IProgressMonitor pm) {
        Lock wl = listLock.writeLock();
        try {
          wl.lock();
          listeners.add(sl);
        }finally {
          wl.unlock();
          
        }
      }
    }, false);
  }

  public void removeStatusListener(IDocumentSocketServiceStateListener sl) {
    JobManager.jm.startJob("remove status listener from socket "+getFSIDFull(), new RunnableWithMonitor() {
      
      @Override
      public void run(IProgressMonitor pm) {
        Lock wl = listLock.writeLock();
        try {
          wl.lock();
          listeners.remove(sl);
        }finally {
          wl.unlock();
          
        }
      }
    }, false);
  }

  public void close() {
    unRegisterSocket(this);
    List<WSSocket> allSocks = null;
    synchronized (resourceNodeToSocks) {
      for(Entry<TreeNode, List<WSSocket>> en:resourceNodeToSocks.entrySet()){
        List<WSSocket> sock = en.getValue();
        if(allSocks==null)
          allSocks = new LinkedList<>();
        allSocks.addAll(sock);
      }
    }
    if(allSocks!=null)
      wsprovider.sendCloseMessage(allSocks, "View is closed", true);
    fireClosedEvent();
  }

  private void fireClosedEvent() {
    Lock l = listLock.readLock();
    l.lock();
    List<IDocumentSocketServiceStateListener> list;
    try {
      list = new LinkedList<>(listeners);
    }finally {
      l.unlock();
    }
    if(list!=null) {
      for(IDocumentSocketServiceStateListener ds:list)
        try {
          ds.close();
        }catch(Exception e) {
          RLogger.logError(e);
        }
    }
  }
  protected boolean isLinked() {
    if(linked == null)
      return false;
    return linked.isLinked();
  }

  public BrowserState getState(String location) {
    return states.get(location);
  }

  public void setState(String location, BrowserState s) {
    states.put(location, s);
  }

  public boolean isFullReady() {
    return true;
  }

  public boolean isReady(String v) {
    return states.getOrDefault(v,BrowserState.NEW).equals(BrowserState.READY);
  }

  public void dispose(boolean stopNow){
    if(isDisposed)
      return;
    RLogger.logDebug("actual dispose called on "+sess+" "+getFSIDFull() +" "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()),6);
    close();
    ITreeChangeListener gtl = getGUITreeChangeListener();
    ITreeChangeListener tl = getTreeChangeListener();
    ITreeChangeListener ctl = getGenericTreeChangeListener();
    
    for(TreeDB db: listenedTrees) {
      if(gtl!=null)
        db.removeGUIListener(gtl);
      if(tl!=null) {
        db.removeListener(tl);
      }
      if(ctl!=null)
        db.removeListener(ctl);
      db.removeListener(disposedListener);
    }
    for(IResTracker rt:listenedTrackers) {
      rt.removeListener(Moment.BEFORE, tsl);
      rt.getTreeStateManager().removeStateHook("*", LISTENED_STATES, treeStateListener);
    }
    WSServer wserv = getWSProvider(true);
    if(wserv!=null) {
      wserv.unRegisterMessageAndStateListeners(coListener, this);
      wserv.closeUsage(getFSIDFull());
    }
    isDisposed = true;
    List<WSSocket> allSockets = null;
    Map<TreeNode, List<WSSocket>> socketsState = new HashMap<>();
    synchronized (resourceNodeToSocks) {
      for(Entry<TreeNode, List<WSSocket>> en:resourceNodeToSocks.entrySet()) {
        socketsState.put(en.getKey(), new LinkedList<>(en.getValue()));
      }
    }
    for(Entry<TreeNode, List<WSSocket>> en:socketsState.entrySet()){
      TreeNode dc = en.getKey();
      List<WSSocket> sock = en.getValue();
      TreeDB dba = dc.getTreeDB();
      if(listenedTrees.remove(dba)) {
        dba.removeGUIListener(getGUITreeChangeListener());
        dba.removeListener(getTreeChangeListener());
        dba.removeListener(getGenericTreeChangeListener());
        dba.removeListener(disposedListener);
      }
      if(allSockets == null)
        allSockets = new LinkedList<>();
      allSockets.addAll(sock);
    }
    if(allSockets!=null)
      for(WSSocket wsc: allSockets)
        dispose(wsc, null, true, stopNow);
    if(listenedTrees.remove(db)) {
      db.removeGUIListener(getGUITreeChangeListener());
      db.removeListener(getTreeChangeListener());
      db.removeListener(getGenericTreeChangeListener());
      db.removeListener(disposedListener);
    }
    synchronized (GenericSocketService.this) {
      dropDisposeTimer(true);
    }

  }
  boolean testIsReadOnly(TreeDB db) {
    if(db == null)
      return true;
    TreeStateManager tsm = db.getTracker().getTreeStateManager();
    RevisionInfo ri = TreeStateManager.getRevisionInfo(db);
    if(db.isDisposed())
      return true;
    TreeState state = tsm.getState(db.getLocalName(), ri);
    return state != TreeState.READY;
  }
  public void dispose(WSSocket ws,Session session, boolean stopAfter, boolean stopNow) {
    if(isDisposed)
      return;
    closeInitedSession(ws, stopAfter);
    ws.sessionDisposed(session, stopAfter);
    if(stopAfter && ws.inited == null)
      updateDisposeTimer(stopNow?0:TIMER_TO_DISPOSE, false);
    
//    if(ws!=null)
//      ws.dispose();
  }
  private RunnableWithMonitor disposeTask = null;
  private boolean disposeIsOnInit = false;
  private boolean disposeStarted = false;
  private int disposePaused = 0;
  private long disposeTime = -1; 
  private long savedDisposeTime = -1;
  private void dropDisposeTimer(boolean dispose) {
    RLogger.logDebug("timer dropped on "+sess+" "+getFSIDFull()+" "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()),6);
    
    synchronized (this) {
      disposeTime = -1;
      disposePaused = dispose?-1:0;//final dispose, set to -1 to recognize it
      disposeStarted = false;
      savedDisposeTime = -1;
      disposeTask = null;
    }
  }
  private void updateDisposeTimer(boolean disposeIsOnInit){
    updateDisposeTimer(TIMER_TO_DISPOSE, disposeIsOnInit);
  }
  public void pauseDisposeTimer() {
    RLogger.logDebug("pause call on "+sess+" "+getFSIDFull()+" "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()),6); 
    synchronized (this) {
      if(disposePaused == -1)
        return;
      disposePaused++;
      disposeStarted = disposeTime>-1;
      if(disposeStarted)
        if(savedDisposeTime!=-1)
          savedDisposeTime = disposeTime-System.currentTimeMillis();
    }
  }
  public void unpauseDisposeTimer() {
    RLogger.logDebug("unpause on "+sess+" "+getFSIDFull()+" "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()),6);
    boolean updateTimer = false;
    synchronized (this) {
      if(disposePaused>0) {
        disposePaused--;
        if(disposeStarted && disposePaused==0)
        {
          if(disposeIsOnInit && !currentInitedSockets.isEmpty())
            return;//special case when we are unpause dispose on page load by resources and
                  //may potentially fall into race
          updateTimer = true;
        }
      }
    }
    if(updateTimer)
      updateDisposeTimer(START_DISPOSE_TIMER, true);//this timer change tends to be only on start
  }
  private String timerStack = null;
  private Set<WSSocket> wasInited = new HashSet<>();
  private void updateDisposeTimer(int timeToDispose, boolean disposeIsOnInit) {
    boolean taskInited = false;
    synchronized (this) {
      if(disposePaused>0)//during pause we ignores dispose timer calls
      {
        disposeStarted = Math.max(System.currentTimeMillis()+timeToDispose, disposeTime)>-1;
        return;
      }
      this.disposeIsOnInit = disposeIsOnInit; 
      timerStack = CoreUtils.getTrace(Thread.currentThread().getStackTrace());
      RLogger.logDebug("update dispose call(paused? "+disposePaused+") on "+sess+" "+getFSIDFull()+" "+timerStack,6);
      disposeTime = Math.max(System.currentTimeMillis()+timeToDispose, disposeTime);
      savedDisposeTime = disposeTime;
      disposeStarted = disposeTime>-1;
      if(disposeTask == null) {
        taskInited = true;
        disposeTask = new RunnableWithMonitor() {
          @Override
          public void run(IProgressMonitor pm) {
            boolean halt = false;
            while (!halt) {
              if(currentActiveSockets.size()>0 || disposeTask!=this){
                halt = true;
                continue;
              }
              boolean runNow = false;
              boolean disposedState = false;
              long disTime = -1;
              long savedTime = -1;
              int disPaused = 0;
              synchronized (GenericSocketService.this) {
//                disTime = disposeTime;
                disPaused = disposePaused;
                savedTime = savedDisposeTime;
                if(disPaused>0)
                  disTime = System.currentTimeMillis()+savedTime;//move time if we paused
                else 
                  disTime = disposeTime;
                disposedState = isDisposed;
              }
              if(disposedState) {
                halt = runNow = true;
              }else
                if(disTime == -1 || disPaused>0)
                  runNow = false;
                else
                  runNow = halt = System.currentTimeMillis()-disTime>0;
              boolean rerun = false;
              if(disPaused>0)
                rerun = true;
              else
                rerun = !disposedState && disTime!=-1;
              if(!rerun)
              {
                 halt = true;  
              }
              if(disTime == -1)//stopped
                return;
              if(!runNow && rerun)
              {
                try {
                  long waitTime =
                    Math.max(0, 
                      Math.min(disTime-System.currentTimeMillis(), WAIT_INTERVAL));
                  if(waitTime>0)
                    Thread.currentThread().join(waitTime);
                } catch (InterruptedException e) {
                }
                continue;
              }
              RLogger.logDebug("dispose happens call on "+currentActiveSockets+" "+sess+" "+getFSIDFull()+" "+timerStack,6);
              if(currentActiveSockets.size()==0) {
                try {
                  dispose(true);
                }
                catch(Exception e) {
                  e.printStackTrace();
                }  
              } 
              halt = true;
            }
            if(disposeTask == this)
              dropDisposeTimer(false);
          }
        };
      }
    }
    WSServer wserv = getWSProvider();
    String name = wserv!=null?wserv.getPortUserInfo():"NOSOCKET";
    String port = Integer.toString(wserv!=null?wserv.getPort():0);
    if(taskInited)
      JobManager.jm.startBaseJob("Stop socket '"+name+"' "+port, disposeTask, false);
  }
  private void closeInitedSession(WSSocket wss, boolean stopAfter) {
    currentInitedSockets.remove(wss);
    currentActiveSockets.remove(wss);
    if(stopAfter)
      wsprovider.sendCloseMessage(wss, "close session");
    //session.close(1001, "close session");
//    if(wss!=null) {
//      wss.unregisterSocket(session, !stopAfter);
//      if(wss.inited == null) {
//      socket = null;
//      }
//    }
  }

  @Override
  public void handleConnected(WSSocket ws, Session session, String id) {
    if(isDisposed) {
      RLogger.logDebug("call connection on disposed socket "+id+" "+ws.getPort());
      return;
    }
    if(id.equals(getFSIDFull()))
      currentActiveSockets.add(ws);
//    if(actionsOnInit!=null)
//      actionsOnInit.run();
  }

  protected void setSessionInited(WSSocket ws, Session s) {
    if(isDisposed) {
      RLogger.logDebug("call connection on disposed socket "+ws.getPort());//TODO send close to socket
      return;
    }
    WSServer wss = getWSProvider();
    if(wss == null) {
      RLogger.logError("call connection on non-exists server");
      return;
    }
    currentInitedSockets.add(ws);
    dropDisposeTimer(false);//if we started dispose right before then stop it(on f5 for ex.)
  }
  @Override
  public void handleClosed(WSSocket ws, Session session, boolean stopAfter) {
    if(currentInitedSockets.contains(ws))
      dispose(ws, session, stopAfter, false);//if socket is closing - close related resources
    Map<TreeNode, List<WSSocket>> repl = null;
    synchronized (resourceNodeToSocks) {
      for(Entry<TreeNode, List<WSSocket>> en: resourceNodeToSocks.entrySet()) {
        for(WSSocket s:en.getValue()) {
          if(s == ws)
          {
            if(repl==null) 
              repl = new HashMap<>();
            List<WSSocket> wss = en.getValue();
            if(wss!=null) {
              wss = new LinkedList<>();
              wss.remove(ws);
            }
            repl.put(en.getKey(), wss);
          }
        }
      }
      if(repl!=null)
        for(Entry<TreeNode, List<WSSocket>> en:repl.entrySet()) {
          if(en.getValue().isEmpty())
            resourceNodeToSocks.remove(en.getKey());
          else
            resourceNodeToSocks.put(en.getKey(), en.getValue());
        }
    }
    currentActiveSockets.remove(ws);
    currentInitedSockets.remove(ws);
  
  }  
  
  public TreeDB getTreeDB() {
    return db;
  }  
  public void initSocket(String sess) {
    URI uri = getUrl(startNode, sess);
    if(uri!=null) {
      String window = uri.toString();
//      
//      Map<Session, ConcurrentLinkedQueue<JSONObject>> pa =
//          ws.getInit(window, sess);
//      if(pa!=null) {
//        boolean is = false;
//          for(ISocketMessageListener mh: mhs)
//            for(Entry<Session, ConcurrentLinkedQueue<JSONObject>> en:
//              pa.entrySet())
//              for(JSONObject s:en.getValue())
//                if(mh.handleMessage(en.getKey(), (String)s.get("operation"), window, s))
//                  is = true;
//          if(is)
//            RLogger.logError("cannot init socket for sess "+sess);
//      }
    }else 
      RLogger.logError("Problems while initing socket on "+(startNode==null?"NULL NODE":startNode));
  }

  private static Random sessgen = new Random();
  protected String sess = null;
  public String getComplexSession(String location) {
    return WSSocket.formKey(location, sess);
  }
  private void generateSess() {
    long l = sessgen.nextLong();
    sess = Long.toString(l!=Long.MIN_VALUE?Math.abs(l):0);
  }
  public String getSession() {
    return sess;
  }
  protected String filterPath(String window, TreeNode node) {
    if(window == null)
      return null;
    String transf = node!=null&&node.getNodeResourcesPath()!=null
          ?node.getNodeResourcesPath().toString()
          :null;
    try {
      URI u = new URI(CoreUtils.URLEncode(window));
      URI b = new URI(u.getScheme(),null,u.getHost(),u.getPort(),u.getPath(),null,null);
      transf = b.toString();
      if(transf.endsWith("#"))
        transf = transf.substring(0,transf.length()-1);
      if(transf.endsWith("//"))
        transf = transf.substring(0,transf.length()-1);
    
    } catch (URISyntaxException e1) {
    // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    return transf;
  }

  protected WSSocket[] getInitedSocketsArray() {
    return currentInitedSockets.toArray(new WSSocket[0]);
  }
  protected IStateChangeHandler treeStateListener = new AAllTreeStateChangeHandler() {
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState,
      TreeState state) {
      if(listenedTrees.contains(db)) {
        if(state == TreeState.MODIFYING)
        {
          for(WSSocket wss : getInitedSocketsArray())
            wss.send(wss.started, "readOnlyChange", new Pair<>("readOnly", "true"));
        }else if (state == TreeState.READY) {
          for(WSSocket wss : getInitedSocketsArray())
            wss.send(wss.started, "readOnlyChange", new Pair<>("readOnly", "false"));
        }
      }
    }
  };
  
  protected ITreeStateListener tsl =
      new ITreeStateListener() {

        @Override
        public void open(TreeDB db) {
          // TODO Auto-generated method stub
          //add tree change listener
          if (GenericSocketService.this.db == db) {
              ITreeChangeListener gtl = getGUITreeChangeListener();
              if(gtl!=null && !db.getGUIListeners().contains(gtl))
                db.addGUIListener(gtl);
              ITreeChangeListener tl = getTreeChangeListener();
              if(tl!=null && !db.getListeners().contains(tl))
                db.addListener(tl);
              ITreeChangeListener getl = getGenericTreeChangeListener();
              if(getl!=null && !db.getListeners().contains(getl))
                db.addListener(getl);
              if(!db.getListeners().contains(disposedListener))
                db.addListener(disposedListener);
          }
        }

        @Override
        public void close(TreeDB db) {
          //remove tree change listener

          if ((GenericSocketService.this.db != db)) {
            return;
          }
          ITreeChangeListener gtl = getGUITreeChangeListener();
          if(gtl!=null)
            db.removeGUIListener(gtl);
          ITreeChangeListener tl = getTreeChangeListener();
          if(tl!=null)
            db.removeListener(tl);
          ITreeChangeListener getl = getGenericTreeChangeListener();
          if(getl!=null)
            db.removeListener(getl);
          db.removeListener(disposedListener);
          dispose(true);
        }

        @Override
        public void delete(String lname, String pname, TreeDB db) {
          if (GenericSocketService.this.db!=db) {
            return;
          }
          dispose(true);
        }

        @Override
        public void settingsChanged(TreeDB db) {
          //TODO update content
        }
      };
  protected abstract URI getUrl(TreeNode startNode, String session);
  protected abstract ISocketMessageListener getMessageListener();
  protected abstract ITreeChangeListener getTreeChangeListener();
  protected abstract ITreeChangeListener getGUITreeChangeListener();
  private ITreeChangeListener gcl = new ITreeChangeListener() {
    
    @Override
    public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
    }
    
    @Override
    public void deleted(TreeNode nodeDesc, byte flag, Moment m) {
      if(startNode == nodeDesc)
        dispose(true);
    }
    
    @Override
    public void created(TreeNode node, byte flag) {
    }
    
    @Override
    public void childrenOrderChanged(TreeNode node, byte flag) {
    }
    
    @Override
    public void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
    }
    
    @Override
    public void changed(List<TreeNodeChangeEvent> changes) {
    }
    
    @Override
    public void beforeCreated(NodeDesc node, byte flag) {
    }
  };
  protected final ITreeChangeListener getGenericTreeChangeListener(){
    return gcl;
  }
  private static Map<String, List<GenericSocketService>> socks = 
      new ConcurrentHashMap<>();
  private static String getNodeId(TreeDB db2, TreeNode startNode2) {
    return db2.getLocalName()+"::"+startNode2.getSUUId();
  }
  private static String getCombinedId(String nodeId, String port) {
    return nodeId+"-"+port;
  }
  private static void unRegisterSocket(GenericSocketService service) {
    String nodeId = getNodeId(service.db, service.startNode);
    Integer porti = service.getPort(true);
    String port = porti == null ? "" : Integer.toString(porti);
    String sid = getCombinedId(nodeId, port);
    socks.getOrDefault(sid, Collections.emptyList()).remove(service);
  }
  private static void registerSocket(GenericSocketService genericSocketService) {
    //TODO for documents with frames we need to init on all related
    String nodeId = getNodeId(genericSocketService.db, genericSocketService.startNode);
    Integer porti = genericSocketService.getPort();
    if(porti!=null) {
      String port = Integer.toString(porti);
      String sid = getCombinedId(nodeId, port);
      List<GenericSocketService> val = socks.get(sid);
      if(val == null) {
        val = new CopyOnWriteArrayList<>();
        socks.put(sid, val);
      }
      val.add(genericSocketService);
    }
  }
  //used now for document-related sockets 
  public static List<GenericSocketService> detectSocket(String port, String nodeId) {
    String sid = getCombinedId(nodeId, port);
    return socks.getOrDefault(sid, Collections.emptyList());
  }
  public boolean registerResourceRelatedSocket(WSSocket ws, TreeNode doc2, TreeNode docPar) {
    boolean added = false;
    synchronized (resourceNodeToSocks) {
      List<WSSocket> oarr = resourceNodeToSocks.get(doc2);
      if(oarr == null) {
        oarr = new LinkedList<WSSocket>();
        resourceNodeToSocks.put(doc2, oarr);
      }
      added = oarr.add(ws);
    }
    if(added) {
      TreeDB dba = doc2.getTreeDB();
      if(listenedTrees.add(dba)) {
        ITreeChangeListener glist = getGUITreeChangeListener(),
           tlist = getTreeChangeListener(),
           genlist = getGenericTreeChangeListener();
        if(glist!=null && !dba.getGUIListeners().contains(glist))
          dba.addGUIListener(glist);
        if(tlist!=null && !dba.getListeners().contains(tlist))
          dba.addListener(tlist);
        if(genlist!=null && !dba.getListeners().contains(genlist))
          dba.addListener(genlist);
        if(!dba.getListeners().contains(disposedListener))
          dba.addListener(disposedListener);
      }
      IResTracker tracker = dba.getTracker();
      if(listenedTrackers.add(tracker)) {
        tracker.addListener(Moment.BEFORE, tsl);
        tracker.getTreeStateManager().addStateHook("*", LISTENED_STATES, treeStateListener);
      }
      if(docPar!=null)
        sateliteNodeParent.put(doc2, docPar);
      return true;
    }
    return false;
  }

  public boolean isDisconnectedFromView() {
    return viewDisconnected;
  }
  public void setDisconnectedFromView(boolean value) {
    viewDisconnected = value;
  }
}
