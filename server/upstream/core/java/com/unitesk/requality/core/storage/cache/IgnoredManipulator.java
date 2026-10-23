package com.unitesk.requality.core.storage.cache;

import java.util.Iterator;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.IStoppable;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class IgnoredManipulator extends CacheManipulator<TreeDB> implements IStoppable {
  private static String NAME="Ignored manipulator";
  private boolean stopped = false;
  public static final String IGNORE_CMAN_NAME = "ignoreSection";
  
  private IgnoreCacheMan man;
  
  public IgnoredManipulator(TreeDB db){
    man = new IgnoreCacheMan(db, false);
  }
  
  @Override
  public OperationResult innerDropCache(TreeDB db, SettingsManipulator cman) {
    getMan().removeAll(db);
    return OperationResult.SUCEEDED;
  }

  @Override
  public CacheManipulatorInitializationStage getCacheState(SettingsManipulator sman, TreeDB treeDB, CacheInitializationStage stage) {
    if(stage ==CacheInitializationStage.FULL || stage == CacheInitializationStage.INIT)
      return super.getCacheState(sman, treeDB, stage);
    return CacheManipulatorInitializationStage.NON_APPLIABLE;
  }
  @Override
  public OperationResult innerInitFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    stopped = false;
    Global.registerStoppable(this);
    try{
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getRootNode(),true,false,true);
      Iterator<TreeNode> it = dftw.iterator();
      while(it.hasNext()){
        if(stopped || mon.isCanceled())
          return OperationResult.UNSUCEEDED;
        TreeNode node = it.next();
        TreeNode par = node.getWorkParent();
        if(par!=null)
          if(Global.getRepoManager(db).getIsDirectlyIgnored(par, node, node.getId(), false)) {
            if(mon.isCanceled()) 
              return OperationResult.UNSUCEEDED;
            getMan().setIgnored(node, true);
          }
        
      }
    }
    finally{
      Global.unRegisterStoppable(this);
    }
    return OperationResult.SUCEEDED;
  }
  public IgnoreCacheMan getMan(){
    return man;
  }
  public Boolean getIgnored(TreeNode nde) {
    return getMan().getIgnored(nde);
  }
  public void setIgnored(TreeNode node, boolean ig) {
    getMan().setIgnored(node,ig);
  }

  @Override
  public boolean isCrossProjectCache() {
    return false;
  }

  @Override
  public void close() {
    // TODO Auto-generated method stub
    
  }

  @Override
  public OperationResult innerInitNotFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    stopped = false;
    return OperationResult.SUCEEDED;
  }
  private static String[]C_NAMES=new String[]{IGNORE_CMAN_NAME};
  private static String[]C_DEFS = new String[]{"1.2.1"};
  
  @Override
  protected String[] getSettingsNames(TreeDB db) {
    return C_NAMES;
  }

  @Override
  protected String[] getVersions() {
    return C_DEFS;
  }

  @Override
  public void stop() {
    stopped = true;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected boolean isNonRevision() {
    return false;
  }
}
