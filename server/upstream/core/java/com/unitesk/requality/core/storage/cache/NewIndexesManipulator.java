package com.unitesk.requality.core.storage.cache;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.OperationResult;

public class NewIndexesManipulator extends CacheManipulator<TreeDB>{
  private static final String NAME="New index manipulator";
  private static final String NINDEX_MANAGER="newIndexesManager";
  private NewIndexesCacheMan nicm;
  private Set<UUID> set;
  private Set<UUID> rset;
  public NewIndexesManipulator(TreeDB db) {
    nicm = new NewIndexesCacheMan(db, false);
  }
  @Override
  protected OperationResult innerInitFirst(TreeDB db, SettingsManipulator sman, IProgressMonitor mon) {
    set = new HashSet<>();
    rset = new HashSet<>();
    return OperationResult.SUCEEDED;
  }

  @Override
  public CacheManipulatorInitializationStage getCacheState(SettingsManipulator sman, TreeDB treeDB, CacheInitializationStage stage) {
    if(stage == CacheInitializationStage.FULL || stage == CacheInitializationStage.INIT)
      return super.getCacheState(sman, treeDB, stage);
    return CacheManipulatorInitializationStage.NON_APPLIABLE;
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
  public OperationResult innerInitNotFirst(TreeDB db, SettingsManipulator sman, IProgressMonitor mon) {
    set = nicm.getAll();
    rset = new HashSet<>();
    return OperationResult.SUCEEDED;
  }
  private static String[] C_NAMES = new String[]{NINDEX_MANAGER};
  private static String[] C_DEFS = new String[]{"1.2.1"};
  
  @Override
  protected String[] getSettingsNames(TreeDB db) {
    return C_NAMES;
  }

  @Override
  protected String[] getVersions() {
    return C_DEFS;
  }


  public boolean isNew(UUID node) {
    return set!=null && set.contains(node);
  }
  public void addNewIndex(TreeNode node) {
    if(set == null || set.isEmpty())
      set = new HashSet<>();
    set.add(node.getUUId());
    nicm.add(node.getTreeDB(), node.getUUId());
  }
  public void addNewIndex(TreeDB db, UUID node) {
    if(set == null || set.isEmpty())
      set = new HashSet<>();
    set.add(node);
    nicm.add(db, node);
  }

  public void removeNewIndex(TreeDB db, UUID node) {
    if(set!=null && !set.isEmpty())
      set.remove(node);
    nicm.remove(db, node);
  }

  
  @Override
  protected OperationResult innerDropCache(TreeDB db, SettingsManipulator cman) {
    if(set!=null && !set.isEmpty())
      set.clear();
    if(rset!=null && !rset.isEmpty())
      rset.clear();
    nicm.removeAll(db);
    return OperationResult.SUCEEDED;
  }
  @Override
  public void clearNodeCache(TreeDB db, SettingsManipulator sm, TreeNode node) {
    removeNewIndex(db, node.getUUId());
  }
  public void addRemovedNewIndex(TreeDB treeDB, UUID uuid) {
    if(rset!=null)
      rset.add(uuid);
  }
  public boolean isRemovedNew(UUID uid) {
    return rset!=null && rset.contains(uid);
  }
  @Override
  public String getName() {
    return NAME;
  }
  @Override
  protected boolean isNonRevision() {
    return true;
  }

}
