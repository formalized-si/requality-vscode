package com.unitesk.requality.repo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.events.INodeCacheEventListener;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;

public class DummyReportNodeCacheListener implements INodeCacheEventListener{

  private int cur, last = 0;
  private double step;
  private Set<UUID> wasc = new HashSet<>();
  private IProgressMonitor monitor;
  private boolean started = false;
  private TreeDB db;
  public DummyReportNodeCacheListener(TreeDB db, int steps, IProgressMonitor mon){
    this.monitor = mon;
    this.db = db;
    this.cur = 0;
    this.step = steps*1.0d/mon.getMax();
  }
  
  @Override
  public void newNodeInCache(UUID uid, TreeNode node) {
  }
  
  public void initMainGeneration(){
    started = true;
  }

  @Override
  public void getCacheRequest(TreeDB db, UUID uid) {
    if(this.db!=db || !started)
      return;
    //now = 0;
    //last = 0;
    if(wasc.add(uid)){
      cur++;
      int newv = (int)(cur*step);
      if(newv>last && !monitor.isCanceled())
      {
        //now+=(newv-last)*step;
        monitor.worked(newv-last);
        last = newv;
      }
    }
  }}
