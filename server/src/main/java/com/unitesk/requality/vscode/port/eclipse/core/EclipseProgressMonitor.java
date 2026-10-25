package com.unitesk.requality.vscode.port.eclipse.core;

import java.util.LinkedList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.operation.IProgressMonitor;

public class EclipseProgressMonitor implements IProgressMonitor {
  private com.unitesk.requality.vscode.ui.runtime.IProgressMonitor monitor;
  private int max = com.unitesk.requality.vscode.ui.runtime.IProgressMonitor.UNKNOWN;
  private int monPos = 0;
  //private int pos = 0;
  private LinkedList<Long> taskStarts = new LinkedList<>();
  private LinkedList<Long> taskMax = new LinkedList<>();
  private LinkedList<Long> taskCnt = new LinkedList<>();
  private LinkedList<String> taskName = new LinkedList<>();
  boolean inited = false;
  public EclipseProgressMonitor(com.unitesk.requality.vscode.ui.runtime.IProgressMonitor monitor) {
    this.monitor = monitor;
  }
  public com.unitesk.requality.vscode.ui.runtime.IProgressMonitor getRaw(){
    return monitor;
  }
  @Override
  public void worked(int val) {
    Long last = taskCnt.pollLast();
    if(last == null)
      last = 0L;
    taskCnt.addLast(last+val);
    Long maxv = taskMax.peekLast();
    if(maxv == 0)
      maxv = 1L;
    int newMonPos = (int) (last*max/maxv); 
    monitor.worked(newMonPos-monPos);
    monPos = newMonPos;
  }

  @Override
  public void doneAll() {
    while(taskName.size()>0)
      doneTask();
    monitor.done();
   
  }
  @Override
  public void beginTask(String string, int taskMax) {
    Long ms = System.currentTimeMillis(); 
    if(monitor.isCanceled())
      return;
    if(!inited){
      max = taskMax;
      if(max<10000)
        max = 10000;
      inited = true;
      monitor.beginTask(string, max);
    }else{
      monitor.subTask(string);
      monPos = 0;
    }
    this.taskMax.addLast((long) taskMax);
    taskName.addLast(string);
    taskCnt.addLast((long) 0);
    taskStarts.addLast(ms);
  }

  @Override
  public boolean isCanceled() {
    return monitor.isCanceled();
  }
  @Override
  public int getRest() {
    if(taskMax.size() == 0)
      return max-monPos;
    return (int) (taskMax.peekLast()-taskCnt.peekLast());
  }
  @Override
  public int getMax() {
    return max;
  }
  @Override
  public void doneTask() {
    int newMon = 0;
    if(taskStarts.size()>0) {
      String name = taskName.pollLast();
      long tspend = System.currentTimeMillis()-taskStarts.pollLast();
      RLogger.logDebug("task "+name+" done. Time spend "+tspend,4);
      taskCnt.pollLast();
      taskMax.pollLast();
      if(taskCnt.size()>0) {
        Long peek = taskMax.peekLast();
        newMon = (int) (taskCnt.peekLast()*max/(peek!=0?peek:1));
      }
    };
    if(taskStarts.size() == 0)
      inited = false;
    monitor.worked(newMon-monPos);
    monPos = newMon;
  }
  public static EclipseProgressMonitor from(
      com.unitesk.requality.vscode.ui.runtime.IProgressMonitor monitor2) {
    if(monitor2 == null)
      return null;
    return new EclipseProgressMonitor(monitor2);
  }
  @Override
  public void cancel() {
	monitor.setCanceled(true);
  }
  @Override
  public String getTask() {
    return taskName.isEmpty()?null:taskName.peekLast();
  }
  @Override
  public boolean isDBLocal() {
    return false;//always operations one?
  }
}
