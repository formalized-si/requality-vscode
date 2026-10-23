package com.unitesk.requality.core.storage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;

public abstract class TrackerNotifier {
  private List<ITreeStateListener> listenersBefore = new CopyOnWriteArrayList<>();
  private List<ITreeStateListener> listenersAfter = new CopyOnWriteArrayList<>();
  public boolean addListener(Moment m, ITreeStateListener l) {
    if(m == Moment.BEFORE) {
      if(listenersBefore.contains(l))
        return false;
      return listenersBefore.add(l);
    } else {
      if(listenersAfter.contains(l))
        return false;
      return listenersAfter.add(l);
    }
  }
  public boolean removeListener(Moment m, ITreeStateListener l) {
    if(m == Moment.BEFORE) {
      return listenersBefore.remove(l);
    } else {
      return listenersAfter.remove(l);
    }
  }
  public synchronized void notifyListeners(Moment moment, String lname, String pname, TreeDB db, TreeEvent type) {
	if(db == null)
      return;
	List<ITreeStateListener> listeners = moment == Moment.BEFORE? listenersBefore: listenersAfter;
	for (ITreeStateListener listener : listeners) {
      switch (type) {
        case TREE_CLOSE:
          {
            listener.close(db);
          }
          break;
        case TREE_OPEN:
          {
            listener.open(db);
          }
          break;
        case TREE_DELETE:
          {
            //                  TreeDBBackupRepeatingJob tbr = smap.get(db);
            //                  if(tbr!=null)
            //                      tbr.singleAndStop();
            //db.clearAllCaches(false);
            listener.delete(lname,pname, db);
          }
          break;
        case SETTS_CHANGED:
          {
            listener.settingsChanged(db);
          }
          break;
      }
    }
	//TODO move to separate class
    switch (type) {
      case TREE_CLOSE :{
        if(!db.isDisposed())
          treeClosed(db);
      }break;
      case TREE_OPEN:{
        treeOpenned(db);
      }break;
      case TREE_DELETE:{
        if(!db.isDisposed()){
          db.getTracker().getCPCacheStorage(db).reactProjectDeleted(db, db.getTracker(), lname, pname);
          treeDeleted(db);
        }
      }break;
      case SETTS_CHANGED:{
        treeSettingsChanged(db);
      }break;
      default:
        break;
    }
  }
  protected void treeDeleted(TreeDB db) {
    // TODO Auto-generated method stub
    
  }
  protected void treeOpenned(TreeDB db) {
    // TODO Auto-generated method stub
    
  }
  protected void treeClosed(TreeDB db){
    
  };
  protected void treeSettingsChanged(TreeDB db){
    
  };
}
