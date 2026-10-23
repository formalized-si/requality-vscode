package com.unitesk.requality.core.storage.cache;

import java.net.URI;

import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.OperationResult;

public class SettingsManipulator<T> extends CacheManipulator<T> {
  private static String NAME="SettingsManipulator";
  private ProjSettsCacheMan man;
  private boolean isCp;
  public SettingsManipulator(ICacheStorageProvider db) {
    man = new ProjSettsCacheMan(db, false);
    isCp = false;
  }
  public SettingsManipulator(IResTracker tr) {
    man = new ProjSettsCacheMan(tr, true);
    isCp = true;
  }
  
  @Override
  protected OperationResult innerDropCache(T db, SettingsManipulator cman) {
    //man.removeAll();
    return OperationResult.UNSUCEEDED;//cannot be cleared?
  }
  @Override
  protected OperationResult innerInitFirst(T db, SettingsManipulator cman, IProgressMonitor mon) {
    return OperationResult.SUCEEDED;
  }
  public boolean getBooleanSetting(String name, boolean def) {
    Object val = man.getProjectSetting(name);
    if(val instanceof String)
      return val.equals("1")||Boolean.parseBoolean((String)val);
    return val == null?def:(boolean)val;
  }
  public String getSetting(String name, String def) {
    Object val = man.getProjectSetting(name);
    return val == null?def:(String)val;
  }
  public String getLocation() {
    URI loc = man.getStorage().connectionPathFile;
    if(loc == null)
      return "";
    return loc.toString();
  }
  public void setProjectSetting(String name, Object value) {
    man.setProjectSetting(name, value);
  }
  @Override
  public boolean isCrossProjectCache() {
    return isCp;
  }
  @Override
  public void close() {
    // TODO Auto-generated method stub
    
  }
  public static SettingsManipulator init(Object db) {
    if(db instanceof IResTracker)
      return new SettingsManipulator((IResTracker) db);
    if(db instanceof ICacheStorageProvider)
      return new SettingsManipulator((ICacheStorageProvider) db);
    return null;
  }
  @Override
  public OperationResult innerInitNotFirst(T db, SettingsManipulator cman, IProgressMonitor mon) {
    // TODO Auto-generated method stub
    return OperationResult.SUCEEDED;
  }

  private static String[] S_NAMES = new String[0];
  private static String[] S_DEFS = new String[0];
  @Override
  protected String[] getSettingsNames(T db) {
    return S_NAMES;
  }
  @Override
  protected String[] getVersions() {
    return S_DEFS;
  }
  @Override
  public String getName() {
    return NAME;
  }
  @Override
  protected boolean isNonRevision() {
    //TODO think
    return false;
  }
  @Override
  public CacheManipulatorInitializationStage getCacheState(SettingsManipulator cman, T treeDB, CacheInitializationStage stage) {
    if (stage == CacheInitializationStage.FULL || stage == CacheInitializationStage.STORAGE_INIT)
      return super.getCacheState(cman, treeDB, stage);
    return CacheManipulatorInitializationStage.NON_APPLIABLE;
  }
}
