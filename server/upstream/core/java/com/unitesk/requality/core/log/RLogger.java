package com.unitesk.requality.core.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.tool.CoreUtils;

public class RLogger {
  private static String LOG_FOLDER = "logs";
  private static String LOG_FILE_PREFIX = "requality";
  private static String LOG_FILE_NAME=LOG_FOLDER+File.separator+LOG_FILE_PREFIX;
  private static PrintStream wr, wer;
  private static PrintStream log;
  private static AtomicBoolean haltAppend = new AtomicBoolean(false);
  private static ConcurrentLinkedQueue<String> messanges = new ConcurrentLinkedQueue<String>();
  public static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
      filedtf = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"); 
  static{
    wr = System.out;
    wer = System.err;
    try {
      File logFile = new File(System.getProperty("user.dir")+File.separator+LOG_FILE_NAME+getNow(filedtf)+".log");
      if(!logFile.getParentFile().exists())
        logFile.getParentFile().mkdirs();
      removeOldLogs(logFile.getParentFile(),10);
      log = new PrintStream(logFile);

      startLogUpdater();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  public static LoggerSource baseLogger = new LoggerSource();
  public static LoggerSource loggerS = baseLogger;
  public void lI(String s){
    s = "I["+getNow()+"]: "+s;
    wr.println(s);
    addToLog(s);
  }
  private static void startLogUpdater() {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        haltAppend.set(true);
      }
    }));

    JobManager.jm.startBaseJob("append log with messanges", new RunnableWithMonitor() {
      @Override
      public void run(IProgressMonitor pm) {
        while (true)
        {
          String messange = messanges.poll();
          if(messange == null)
            try {
              if(haltAppend.get())
                return;
              Thread.currentThread().join(2000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          else
            log.append(messange);
          
        }
      }
    }, false); 
  }
  private static void removeOldLogs(File parentFile, int limit) {
    SortedMap<LocalDateTime, File> prevLogs = new TreeMap<>(new Comparator<>() {

      @Override
      public int compare(LocalDateTime o1, LocalDateTime o2) {
        return o2.compareTo(o1);
      }
      
    });
    for(File f:parentFile.listFiles())
    {
      String name = f.getName();
      try {
        if(name.startsWith(LOG_FILE_PREFIX)) {
          LocalDateTime d = LocalDateTime.from(filedtf.parse(name.substring(LOG_FILE_PREFIX.length(), name.length()-4)));
          if(d!=null)
            prevLogs.put(d, f);
        }
      }catch(Exception ex) {
      }
    }
    Iterator<Entry<LocalDateTime, File>> it = prevLogs.entrySet().iterator();
    int cnt = 0;
    while(it.hasNext())
    {
      Entry<LocalDateTime, File> en = it.next();
      cnt++;
      if(cnt>=limit)
        en.getValue().delete();
    }
  }
  private static String getNow() {
    return getNow(dtf);
  }
  private static String getNow(DateTimeFormatter dtf) {
	return dtf.format(LocalDateTime.now());
  }
  public static void addToLog(String o){
    if(log!=null){
      messanges.add(o+"\n");
    }
  }
  public static void logInfo(Object s){
    if(s!=null)
      loggerS.getLogger().lI(s.toString());
    else
      loggerS.getLogger().lI("Null");
  }
  public static void logError(String s) {
    if(s!=null)
      loggerS.getLogger().lE(s.toString());
    else
      loggerS.getLogger().lE("Null");
  }
  public static void logDebug(String s) {
	  logDebug(s,1);
  }
  public static void logDebug(String s, int lvl) {
    if(CoreUtils.DEBUG_LEVEL>=lvl){
      if(s!=null)
        loggerS.getLogger().lD(s.toString());
      else
        loggerS.getLogger().lD("Null");
    }else if (CoreUtils.DEBUG_LEVEL>0)
      addToLog("D["+getNow()+"]: "+s);
  }
  public static void logDebug(Exception e, int lvl) {
    String s = e.getMessage()+"\n"+CoreUtils.getTrace(e); 
    logDebug(s, lvl);
    addToLog("D["+getNow()+"]: "+s);
  }
  public void lE(String s) {
    s = "E["+getNow()+"]: "+s;
    wer.println(s);
    addToLog(s); 
  }
  public void lD(String s) {
    s = "D["+getNow()+"]: "+s;
    wr.println(s); 
    addToLog(s);
  }
  public static void logError(Exception e) {
    String s = "E["+getNow()+"]: "+e.getMessage()+"\n"+CoreUtils.getTrace(e); 
    logError(s);
    addToLog(s);
  }
}
