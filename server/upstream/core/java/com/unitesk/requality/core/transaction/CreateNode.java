package com.unitesk.requality.core.transaction;

import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.tool.CoreUtils;

public class CreateNode extends TreeOperation {
  private String templ;
  private NodeDescSet desc;

  public CreateNode(boolean derived, NodeDescSet desc, String templ, byte flag) {
    super(derived, flag);
    this.desc = desc;
    this.templ = templ;
  }

  @Override
  public void apply(ITreeStorage storage, IStorageDeltaListener delta) {
    if(CoreUtils.DEBUG_LEVEL>1)
      System.out.println("\t Apply create op "+desc.root.getUUId());
    storage.createNode(desc, templ, getFlag());
  }

  @Override
  public void rollback(ITreeStorage storage,IStorageDeltaListener delta) {
    if(CoreUtils.DEBUG_LEVEL>1)
      System.out.println("\t Revert create op "+desc.root.getUUId());
    storage.remove(desc, getFlag());
  }

  @Override
  public String opToString() {
    return "Create " + desc.root.getId() + "(" + desc.root.getUUId() + ", cnt:"+desc.allNodesCreate.size()+") on " + desc.root.getWorkParentUUID() + " [" + desc.root.getAttributes().toString() + "]";
  }
  public NodeDescSet getNodes() {
    return desc;
  }
}
