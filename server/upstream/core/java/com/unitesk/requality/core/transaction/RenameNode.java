package com.unitesk.requality.core.transaction;

import java.util.UUID;

import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.OFlag;

public class RenameNode extends TreeOperation {
  private String oldId;
  private String newId;
  private UUID uuid;

  public RenameNode(boolean derived, UUID uuid, String oldId, String newId, byte flag) {
    super(derived, flag);
    this.uuid = uuid;
    this.oldId = oldId;
    this.newId = newId;
  }

  @Override
  public void apply(ITreeStorage storage, IStorageDeltaListener delta) {
    storage.changeNodeId(uuid, newId, OFlag.NO_UPDATE);
  }

  @Override
  public void rollback(ITreeStorage storage,IStorageDeltaListener delta) {
    storage.changeNodeId(uuid, oldId, OFlag.NO_UPDATE);
  }

  @Override
  protected String opToString() {
	return "Rename "+uuid+" from "+oldId+" to "+newId;
  }
}
