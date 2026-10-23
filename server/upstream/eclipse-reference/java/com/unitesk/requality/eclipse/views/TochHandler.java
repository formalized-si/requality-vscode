package com.unitesk.requality.eclipse.views;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.progress.UIJob;

import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.ITreeNodeProvider;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.ui.ObjectRule;
import com.unitesk.requality.tool.CoreUtils;

public class TochHandler {
  public static final TochHandler EMPTY = new TochHandler(null);
  private static int INIT_TIME = 25;
  TreeViewer tch = null;
  Runnable runafter = null;
  public TochHandler(TreeViewer v) {
    this(v, null);
  }
  public TochHandler(TreeViewer v, Runnable runafter) {
    tch = v;
    this.viewerRule = new ObjectRule(v);
    this.runafter = runafter;
  }
  static int LIMIT = 25;
  static int LIMIT2 = 100;
  private class NodePair implements Comparable<NodePair>{
    TreeDB db;
    String id;
    UUID uid;
    public NodePair(String id, TreeNode node){
      db = node.getTreeDB();
      this.id = id;
      uid = node.getUUId();
    }
    @Override 
    public boolean equals(Object obj) {
      if(obj instanceof String)
        return id.equals(obj);
      return super.equals(obj);
    }
    @Override
    public int compareTo(NodePair o) {
      return id.compareTo(o.id);
    }
  }
  private ConcurrentMap<String, NodePair> pairs = new ConcurrentHashMap<>();
  private BlockingQueue<NodePair>aset = new LinkedBlockingQueue<>();
  private ReentrantLock rwl = new ReentrantLock(false);
  private long lup = -1;
  private int time = INIT_TIME;
  private UIJob uj = null;
  private ObjectRule viewerRule;
  static TreeNode extractNode(Object o) {
    if(o instanceof TreeNode)
      return(TreeNode)o;
    if(o instanceof IProject)
    {
      IProject p = (IProject) o;
      TreeDB db = null;
	  try {
	    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
		db = et
				  .getTreeDB(p, null, et.getLocalName(p));
	  } catch (IOException e) {
		// TODO Auto-generated catch block
		RLogger.logError(e);
	  }
      if(db!=null)
        return db.getRootNode();
    }
    return null;
  }
  public void addToch(TreeNode... as) {
    if(tch == null)
      return;//empty one
    Object input = tch.getInput();
    TreeNode root = null;
    if(input!=null){
      if(input instanceof ITreeNodeProvider)
        root = ((ITreeNodeProvider)input).getNode();
      if(input instanceof TreeNode)
        root = (TreeNode)input;
    }
    for(TreeNode node:as){
      if(node == null || node.equals(root))
      {
        rwl.lock();
        try{
          final TreeViewer tch = TochHandler.this.tch;
          final Runnable runafter = TochHandler.this.runafter;
          if(!tch.getControl().isDisposed())
            EclipseAPI.getDisplay().asyncExec(new Runnable(){
            
              @Override
              public void run() {
                if(tch.getControl().isDisposed())
                  return;
                tch.refresh(null, true);
      
                if(runafter!=null)
                  runafter.run();
              }
            });
          aset.clear();
          pairs.clear();
        }finally{
          rwl.unlock();
        }
        return;
      }else
      {
        String id = getId(node);
        rwl.lock();
        try{
          NodePair np = pairs.get(id);
          if(np!=null)
            aset.remove(np);
          if(np == null)
            np = new NodePair(id, node);
          pairs.put(id, np);
          aset.add(np);
        }
        finally{
          rwl.unlock();
        }
      }
    }
    try{
      rwl.lock();
      if(uj == null){
        lup = System.currentTimeMillis();
        time = INIT_TIME;
        uj = new UIJob("update reqcnf"){
          
          @Override
          public IStatus runInUIThread(IProgressMonitor monitor) {
            TreeStateManager tsm = null;
    	    if(tch.getControl().isDisposed()){
              uj = null;
              return Status.CANCEL_STATUS;
            }
            boolean irl = false,il = true;
            try{
              Instantiator.readLock();
              il = true;
              // TODO Auto-generated method stub
              rwl.lock();
              irl = true;
               long d = (System.currentTimeMillis()-lup);
               boolean first = true;
               int cnt = 0, max = aset.size();
               while((d<LIMIT2||first)&&(max>0 && (cnt!=max||d>LIMIT))){
                 if(tch.getControl().isDisposed())
                   return Status.CANCEL_STATUS;
                first = false;
                cnt = 0;
                if(aset.size()>0){
                  List<TreeNode> toch = new LinkedList<>();
                  List<NodePair> aupset = new LinkedList<>(aset);
                  rwl.unlock();
                  irl = false;
                  for(NodePair u:aupset)
                    try{
                      toch.add(u.db.getNodeExc(u.uid));
                    }catch(NodeNotFoundException nfe){
                      rwl.lock();
                      irl = true;
                      try{
                        aset.remove(u);
                        pairs.remove(u.id);
                      }finally{
                        rwl.unlock();
                        irl = false;
                      }
                    }
                  
                  try{
                    //rlo = false;
                    Set<TreeNode> fch = new HashSet<>();
                    Set<UUID> exps = new HashSet<>();
                    for(Object o : tch.getExpandedElements()){
                      TreeNode on = extractNode(o);
                      if(on != null && !on.isDisposed()){
                        exps.add(on.getUUId());
                        for(TreeNode onc:on.getShownChildren())
                          if(onc != null)
                            exps.add(onc.getUUId());
                      }
                    }
                    for(TreeDB dba: ResManager.getManager().getTreeDBs())
                    {
                       if(dba.isDisposed()) continue;
                       TreeNode rootNode = dba.getRootNode();
                       if(rootNode == null)
                         continue;//broken db
                       for(TreeNode node: rootNode.getShownChildren()) {
                         exps.add(node.getUUId());
                         for(TreeNode node2: node.getShownChildren()) 
                           exps.add(node2.getUUId());
                       }
                    }
                    TreeDB db = null;
                    boolean ignore = false;
                    for(TreeNode node:toch){
                      if(node == null){
                        fch.add(null);
                        continue;
                      }
                      if(node!=null && (node.isDisposed()||!node.isLoaded()))
                        continue;
                      if(db==null || db!=node.getTreeDB()) {
                    	db = node.getTreeDB();
                    	if(tsm == null)
                    	  tsm = TreeStateManager.getOne(db.getTracker());
                    	ignore = tsm.isDisposed(db.getLocalName(), tsm.getRevisionInfo(db));
                      }
                	  if(ignore)
                	    continue;
                      if(exps.contains(node.getUUId())){
                        fch.add(node);
                        continue;
                      }else
                        try{
                          if(node.getShownParentExc()!=null && 
                            exps.contains(node.getShownParentExc().getUUId())){
                            TreeNode sp = node.getShownParent();
                            if(sp!=null){
                              fch.add(node);
                              continue;
                            }
                          }else
                          {
                            //still not loaded node, can be ignored and be updated when expanded
                            rwl.lock();
                            irl = true;
                            try{
                              String id = getId(node);
                              NodePair np = pairs.remove(id);
                              if(np!=null)
                                aset.remove(np);
                            }finally{
                              rwl.unlock();
                              irl = false;
                            }
                          }
                        }
                        catch(NodeNotFoundException nde){
                          
                        }  
                      cnt++;
                    }
                    final Set<TreeNode> ftoch = fch;
                    if (tch.getControl().isDisposed()) {
                      return Status.CANCEL_STATUS;
                    }
                    //TODO null update? mb bug
                    //Object o = viewer.getInput();
                    //RLogger.logInfo(node.toString());
                    if (ftoch.contains(null)){
                      tch.refresh(true);
                      if(runafter!=null)
                        runafter.run();
                    }
                    else
                    {
                      if(ftoch.size()>0){
                        tch.getControl().setRedraw(false);
                        try{
                          for (TreeNode node : ftoch) {
                            rwl.lock();
                            irl = true;
                            try{
                              String id = getId(node);
                              NodePair np = pairs.remove(id);
                              if(np!=null)
                                aset.remove(np);
                            }finally{
                              rwl.unlock();
                              irl = false;
                            }
                            max--;
                            if (tch.getControl().isDisposed()) {
                              return Status.CANCEL_STATUS;
                            }
                            if (node != null && (node.equals(node.getTreeDB().getRootNode()))) {
                              TreeNode nde = node;
                              IResource r = null;
                              try {
                                r = EclipseTracker.getResourceByNode(nde, LockType.TRY_LOCK);
                              } catch (LockIsBusy e) {
                                RLogger.logDebug(CoreUtils.getTrace(e),7);
                              }
                              if (r != null){
                                tch.refresh(
                                    r.getProject(), true);
                                if(runafter!=null)
                                  runafter.run();
                              }
                              return Status.OK_STATUS;
                            }
                            if (node != null && !node.isDisposed()){
                              tch.refresh(node, true);
                              if(runafter!=null)
                                runafter.run();
                            }
                          }
                        }
                        finally{
                          tch.getControl().setRedraw(true);
                          //tch.refresh(true);
                          if(runafter!=null)
                            runafter.run();
                        }
                        
                      }
                    }
                  }
                  catch(Exception e){
                    RLogger.logError(e);
                  }
                  finally{
                    rwl.lock();
                    irl = true;
                    max = aset.size();
                  }
                  d = (System.currentTimeMillis()-lup);
                }
              }
            }finally{
              if(il)
                Instantiator.readUnlock();
              if(aset.size()>0){
            	if(time<1000)
                  time = time*2;
                uj.schedule(time);
              }else	 
                uj = null;
              if(irl) {
                rwl.unlock();
                irl = false;
              }
            }
          
            return Status.OK_STATUS;
          }
        };
       uj.setRule(viewerRule);
       uj.setPriority(Job.LONG);
       uj.schedule();
      }
    }
    finally{
      rwl.unlock();
    }
  }
  private static String getId(TreeNode node) {
    return node.getTreeDB().getProjectName()+"::"+node.getSUUId();
  };
}
