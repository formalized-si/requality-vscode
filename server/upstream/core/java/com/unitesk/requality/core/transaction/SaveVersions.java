package com.unitesk.requality.core.transaction;

import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;

public class SaveVersions extends TreeOperation {
  private UUID uuid;
  private ChangesSet<Version> changed;

  public SaveVersions(boolean derived, UUID uuid,ChangesSet<Version> ch, Map<String, Version> oldVersion, Map<String, Version> newVersion, byte flag) {
    super(derived, flag);
    //TODO remove old map
    this.uuid = uuid;
    this.changed = new ChangesSet<>(ch);
  }
  @Override
  public String opToString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Save versions ");
    sb.append(uuid);
    return sb.toString();
    // return super.toString();
  }

  @Override
  public void apply(ITreeStorage storage, IStorageDeltaListener delta) {
    storage.saveVersions(uuid, changed, getFlag());
  }

  @Override
  public void rollback(ITreeStorage storage,IStorageDeltaListener delta) {
    storage.saveVersions(uuid, changed.getReverted(), getFlag());
  }
}
