package com.unitesk.requality.core.transaction;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;

public abstract class TreeOperation {
  public final boolean isDerived;
  private byte flag;
  private List<TreeOperation> operationsBefore, operationsAfter;
  String caller;

  public List<TreeOperation> getBeforeOperations() {
    return operationsBefore == null? Collections.emptyList():operationsBefore;
  }

  public List<TreeOperation> getAfterOperations() {
    return operationsAfter == null? Collections.emptyList():operationsAfter;
  }
  public TreeOperation(boolean derived, byte flag) {
    this.flag = flag;
    this.isDerived = derived;
  }
  
  public byte getFlag() {
    return flag;
  }

  public abstract void apply(ITreeStorage storage, IStorageDeltaListener delta);

  public abstract void rollback(ITreeStorage storage, IStorageDeltaListener iStorageDeltaListener);

  public Object getData() {
    return null;
  }

  public void dispose() {}
  protected abstract String opToString();
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if(operationsBefore!=null) {
      sb.append("<");
      for(TreeOperation to:operationsBefore) {
    	sb.append("|");
        sb.append(to.toString());
        sb.append("|");
      }
      sb.append(">");
    }
    sb.append(opToString());
    if(operationsAfter!=null) {
      sb.append("{");
      for(TreeOperation to:operationsAfter) {
    	sb.append("|");
        sb.append(to.toString());
        sb.append("|");
      }
      sb.append("}");
    }
	return sb.toString();
  }
  public void addDerived(TreeOperation saveOperation, boolean rootOperationPerformed) {
    if(rootOperationPerformed)
    {
      if(operationsAfter == null)
        operationsAfter = new LinkedList<>();
      operationsAfter.add(saveOperation);
    }else{
      if(operationsBefore == null)
        operationsBefore = new LinkedList<>();
      operationsBefore.add(saveOperation);
    }
  }
}
