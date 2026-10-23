package com.unitesk.requality.core.transaction;

import java.util.UUID;

import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;

public class MoveNode extends TreeOperation {
  private UUID oldParent;
  private UUID newParent;
  private UUID uuid;

  public MoveNode(boolean derived, UUID uuid, UUID oldParent, UUID newParent, byte flag) {
    super(derived, flag);
    this.uuid = uuid;
    this.oldParent = oldParent;
    this.newParent = newParent;
  }

  @Override
  public void apply(ITreeStorage storage, IStorageDeltaListener delta) {
    storage.moveNodeTo(uuid, newParent, getFlag());
  }

  @Override
  public String opToString() {
    return "Moved " + uuid + " from " + oldParent + " to " + newParent;
  }

  @Override
  public void rollback(ITreeStorage storage,IStorageDeltaListener delta) {
    storage.moveNodeTo(uuid, oldParent, getFlag());
  }
}
