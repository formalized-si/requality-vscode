package com.unitesk.requality.core.transaction;

import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.tool.CoreUtils;

public class DeleteNode extends TreeOperation {
  private NodeDescSet desc;

  public DeleteNode(boolean derived, NodeDescSet desc, byte flag) {
    super(derived, flag);
    this.desc = desc;
    //TODO renew attribtues?
  }
  @Override
  public void apply(ITreeStorage storage, IStorageDeltaListener delta) {
    if(CoreUtils.DEBUG_LEVEL>1)
      System.out.println("\t Apply delete op "+desc.root.getUUId());
    storage.remove(desc, getFlag());
  }

  @Override
  public void rollback(ITreeStorage storage,IStorageDeltaListener delta) {
    if(CoreUtils.DEBUG_LEVEL>1)
      System.out.println("\t Revert delete op "+desc.root.getUUId());
    // if(parentUUId!=null)//TODo bug?
    storage.createNode(desc, TreeDB.EMPTY_TEMPLATE, getFlag());
  }

  @Override
  public String opToString() {
    return "Remove " + desc.root.getUUId() + "(cnt: "+desc.allNodesRemove.size()+") id:" + desc.root.getId() + " par:" + desc.root.getWorkParentUUID();
  }
  public NodeDescSet getNodes() {
    return desc;
  }

}
