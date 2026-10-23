package com.unitesk.requality.repo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;

public class RevisionInfo {
  public static final RevisionInfo NONE = new RevisionInfo("NONE", null);
  public static RevisionInfo NO_REV = new RevisionInfo("",new Date());
  public static final String DATA_FORMAT_TEMPLATE="yyyy-MM-dd' 'HH:mm:ss";
  protected String commit;
  protected final Date date;
  private static Thread formatCleaner = null;
  protected final boolean singleRev;
  @Override
  public String toString() {
    return (commit!=null?commit:"NONE")+(date!=null?"("+date.toString()+")":"(NO DATE)");
  }
  private static ConcurrentHashMap<Thread, SimpleDateFormat> threadSafeDFs = new ConcurrentHashMap<>();
  public static SimpleDateFormat getDateFormat() {
    Thread t = Thread.currentThread();
    synchronized(threadSafeDFs) {
      if(formatCleaner == null) {
       AtomicBoolean cleaningStopped = new AtomicBoolean(false);
       Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
         @Override
         public void run() {
           cleaningStopped.set(true);
         }
       }));
       formatCleaner = new Thread(new Runnable() {
         @Override
         public void run() {
           while(!cleaningStopped.get()) {
             synchronized (threadSafeDFs) {
               List<Thread> toRem = null;
               for(Entry<Thread, SimpleDateFormat> en: threadSafeDFs.entrySet()) {
                 if(!en.getKey().isAlive())
                 {
                   if(toRem == null)
                     toRem = new LinkedList<>();
                   toRem.add(en.getKey());
                 }
               } 
               if(toRem!=null)
                 for(Thread t:toRem)
                   threadSafeDFs.remove(t);
			 }
             try {
               Thread.currentThread().join(1000);
             } catch (InterruptedException e) {
		     }
           }
         }
        });
      }
      SimpleDateFormat sdf = threadSafeDFs.get(Thread.currentThread());
      if(sdf!=null)
        return sdf;
      sdf = new SimpleDateFormat(DATA_FORMAT_TEMPLATE);
      threadSafeDFs.put(Thread.currentThread(), sdf);
      return sdf;
    }
  }
  public RevisionInfo(String commit, Date date, boolean singleRev){
    this.commit = commit;
    this.date = date;
    this.singleRev = singleRev;
  }

  public RevisionInfo(String commit, Date date){
    this(commit, date, true);
  }
  public Date getDate(){
    return date;
  }
  public Date getDate(TreeDB db) {
    return date;
  }
  public static synchronized String getStringDate(Date date){
    if(date == null)
      return null;
    long time = date.getTime();
    try {  
      return getDateFormat().format(time);//getTime used to avoid #13612
    }catch(ArrayIndexOutOfBoundsException aio) {
      RLogger.logError(aio);
      try {
        RLogger.logError(date.toString());//try to log date
        getDateFormat().format(time);
      }catch(Exception e) {
      }
    }
    return "NODATE";
  }
  public String getStringDate(){
    return getStringDate(date);
  }

  public String getRevision() {
    return commit;
  }
  public String getRevision(TreeDB db) {
    return commit;
  }

  public boolean isSingleRev() {
    // TODO Auto-generated method stub
    return singleRev;
  }

  public static boolean isSame(RevisionInfo ri, RevisionInfo bri) {
    if(ri == null)
      ri = NO_REV;
    if(bri == null)
      bri = NO_REV;
    return ri.commit.equals(bri.commit);
  }

  public boolean isNull() {
    return commit == null || commit.equals(NONE.commit);
  }

  public static String toString(TreeDB db, RevisionInfo ri) {
	return ri == null ? "null" : ri.getRevision(db);
  }

}
