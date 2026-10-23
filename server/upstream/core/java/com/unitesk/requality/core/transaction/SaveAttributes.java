package com.unitesk.requality.core.transaction;

import java.util.Arrays;
import java.util.UUID;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.tool.CoreUtils;

public class SaveAttributes extends TreeOperation {
  private UUID uuid;
  //private Map<String, AttributeDefinition> oldAttributes;
  //private Map<String, AttributeDefinition> newAttributes;
  private ChangesSet<Attribute> changed;

  public SaveAttributes(
      boolean derived,
      UUID uuid,
      ChangesSet<Attribute> altered, byte flag) {
    super(derived, flag);
    this.uuid = uuid;
    this.changed = new ChangesSet<>(altered);
  }

  @Override
  public String opToString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Save attributes ");
    sb.append(uuid);
    sb.append(" ");
    sb.append(Arrays.toString(changed.getAll().toArray()));
    for(String s:changed.getAll()){
      Attribute oa = changed.getOld(s);
      Attribute na = changed.getNew(s);
      sb.append(s);
//      if(oa!=null)
//        sb.append(oa.toString());
//      else
//        sb.append("null");
//      sb.append("->");
//      if(na!=null)
//        sb.append(na.toString());
//      else
//        sb.append("null");
      sb.append(";");
    }
    return sb.toString();
    // return super.toString();
  }

  @Override
  public void apply(ITreeStorage storage, IStorageDeltaListener delta) {
    try {
      if(delta!=null && 
        !delta.changeAttributes(uuid, changed, getFlag()))
      storage.saveAttributes(uuid, changed, getFlag());
    }catch(Exception e) {
      RLogger.logError("Problems while applying of node attrs change "+CoreUtils.getTrace(e));
    }
  }

  @Override
  public void rollback(ITreeStorage storage,IStorageDeltaListener delta) {
    try {
      //Map<String,AttributeDefinition> old = AttributeStorage.toDefMap(changed.getOldMap());
      ChangesSet<Attribute> cs = changed.getReverted();
      if(delta!=null && 
        !delta.changeAttributes(uuid, cs, getFlag()))
      storage.saveAttributes(uuid, cs, getFlag());
    }catch(Exception e) {
      RLogger.logError("Problems while applying of node attrs change "+CoreUtils.getTrace(e));
    }
  }
}
