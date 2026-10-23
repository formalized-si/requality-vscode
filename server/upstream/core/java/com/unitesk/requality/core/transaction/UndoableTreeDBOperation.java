package com.unitesk.requality.core.transaction;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;

public class UndoableTreeDBOperation {
 private TransactionStorage tstorage;

  public UndoableTreeDBOperation(TransactionStorage tstorage) {
    this.tstorage = tstorage;
  }

  public boolean redo(IProgressMonitor monitor) throws RuntimeException {
    String name = "Redo "+tstorage.getVisibleName();
    CacheStorage cs = tstorage.getCacheStorage();
    CacheTransactionDescription msg = cs.startCacheTransaction(name,2);
    try {
      Pair<String, ITreeStorage> op = tstorage.getStorage().startOperation(name);
      if(op.getSecond() == null)
        return false;//long operation already performing1
      try{
        tstorage.apply(op.getSecond(), true);
      }
      finally{
        tstorage.getStorage().endOperation(op.getFirst());
      }
    }finally {
      if(msg != null && msg.error == null)
        cs.commit(msg);
    }
    return true;
  }

  public boolean undo(IProgressMonitor monitor) throws RuntimeException {
    String name = "Undo "+tstorage.getVisibleName();
    Pair<String,ITreeStorage> op = tstorage.getStorage().startOperation(name);
    CacheStorage cs = tstorage.getCacheStorage();
    CacheTransactionDescription msg = cs.startCacheTransaction(name,2);
    try {
      if(op.getSecond() == null)
        return false;//long operation already performing
      try{
        tstorage.rollback(op.getSecond(), API.getMonitorManager());
      }
      finally{
        tstorage.getStorage().endOperation(op.getFirst());
      }
    }finally {
      if(msg != null && msg.error == null)
        cs.commit(msg);
    }
    return true;
  }

  public void dispose() {
    tstorage.dispose();
  }

  public String getName() {
    return tstorage.getVisibleName();
  }
}

