package com.unitesk.requality.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.api.IAPICacheProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.CPLinkResolver;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.TrackerCacheManipulatorsManager;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class CacheStorageManager{
  private Map<String, CacheStorage> globalRevisionedCS = new HashMap<>();
  private Map<String, CPLinkResolver> globalRevisionedLR = new HashMap<>();
  private Map<String, TrackerCacheManipulatorsManager> globalManagers = new HashMap<>();
  private Path cpPath = null;
  public CacheStorageManager(Path cpPath) {
    this.cpPath = cpPath;
  }
  public void clear(TreeDB p, boolean del) {
  //TODO local clear
    globalRevisionedCS.clear();
    globalRevisionedLR.clear();
  }
  public CacheStorage getCacheStorage(String id) throws Exception {
    if(id == null)
    {
      throw new RuntimeException("No slice has been set");
//      if(mapSliceRevs == null)
//        mapSliceRevs = new HashMap<String, RevisionInfo>();
//      if(db!=null) {
//        getSlice(dbs, Global.getRepoManager(db), db
//          ,TreeStateManager.getRevisionInfo(db)
//          ,mapSliceRevs);
//      }
    }
    synchronized (globalRevisionedCS) {
      CacheStorage cs = globalRevisionedCS.get(id);
      if(cs == null){
        String cpIdToLong = convertIdToLong(cpPath, id, cs);
        cs = new CacheStorage(IAPICacheProvider.DEFAULT, false, cpPath, cpIdToLong, id,null,null,null);
        globalRevisionedCS.put(id, cs);
      }
      return cs;
    }
  }
  private String convertIdToLong(Path cpPath2, String name, CacheStorage cs) {
    if(cpPath2 == null)
      return null;
    synchronized (CacheStorage.class) {
      Path cpPath = cpPath2.resolve("cpConversion");
      List<String> now = null;
      try {
        now = Files.readAllLines(cpPath);
      } catch (IOException e) {
      }
      boolean added = false;
      try {
        if(now == null) {
          now = new LinkedList<>();
          added = true;
          now.add("0|"+name);
          return "0";
        }else {
          String found = null;
          long max = 0;
          for(String s: now) {
            String fs = s.substring(0, s.indexOf("|"));
            Long l = Long.parseLong(fs);
            if(l > max)
              max = l;
            int ind = s.indexOf("|");
            if(ind>0 && s.substring(ind+1).equals(name))
              found = fs;
          }
          if(found == null)
          {
             String fname = Long.toString(max+1);
             now.add(fname+"|"+name);
             added = true;
             return fname;
          }
          return found;
        }
      }finally{
        if(added)
          try {
            Files.deleteIfExists(cpPath);
            Path parPath = cpPath.getParent();
            if(!Files.exists(parPath))
              Files.createDirectory(parPath);
            Files.write(cpPath, now, StandardOpenOption.CREATE_NEW);
          } catch (IOException e) {
            RLogger.logError(e);
          }
      }
    }
  }
  public CPLinkResolver getLinkResolver(IResTracker tracker, String id) {
  if(id == null)
    {
      throw new RuntimeException("No slice has been set");
//      if(mapSliceRevs == null)
//        mapSliceRevs = new HashMap<String, RevisionInfo>();
//      getSlice(dbs, Global.getRepoManager(dbfrom), dbfrom
//          ,TreeStateManager.getOne(ft).getRevisionInfo(dbfrom)
//          ,mapSliceRevs);
    }
    CPLinkResolver cs = globalRevisionedLR.get(id);
    if(cs == null){
      cs = getCMM(tracker, id).getLinkManipulator();
      //cs.initCache(dbfrom, getCMM(dbfrom).getSettingsStore());
      globalRevisionedLR.put(id, cs);
    }
    return cs;
  }
  public TrackerCacheManipulatorsManager getCMM(IResTracker tracker, String id) {
    if(id == null)
      throw new RuntimeException("No slice has been set");

    TrackerCacheManipulatorsManager tcm = globalManagers.get(id);
    if(tcm == null){
      tcm = new TrackerCacheManipulatorsManager();
      tcm.initCM(tracker, SettingsManipulator.init(tracker), new ConsoleProgressMonitor());//TODO monitor?
      globalManagers.put(id, tcm);
    }
    return tcm;
  }
  public void unload(String sliceId) {
    globalRevisionedCS.remove(sliceId);
    globalRevisionedLR.remove(sliceId);
  }
}
