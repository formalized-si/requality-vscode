/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.eclipse.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.ProgressRunnable;
import com.unitesk.requality.core.storage.AbstractTreeStorage;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.DeleteNodeResources;
import com.unitesk.requality.core.storage.IContainerAble;
import com.unitesk.requality.core.storage.IContainerAbleRefresher;
import com.unitesk.requality.core.storage.IFileAble;
import com.unitesk.requality.core.storage.IResourceAble;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.IUUIDsCachedTreeStorage;
import com.unitesk.requality.core.storage.NodeMapper;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.ResourceMap;
import com.unitesk.requality.core.storage.StorageDeleteOperation;
import com.unitesk.requality.core.storage.cache.CacheManipulatorInitializationStage;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.storage.formatversion.LegalicyNodeMapper;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.nodetype.ErrorNode;
import com.unitesk.requality.tool.CoreUtils;

public class ResourceStorage extends AbstractTreeStorage implements IUUIDsCachedTreeStorage {
  public static final String RESOURCE_SUFFIX = ResManager.RESOURCES_SUFFIX;
  private static final String ROOT_NODE_FILENAME = "root.json";
  private Map<UUID,Map<String,AttributeDefinition>> activeAttributes =
      new HashMap<>();
  private IProject project;
  UUID rootNodeUUID = null;
  private ISchedulingRule activeRules = null;
  private Thread activeThread = null;
  
  public static Map<IProject, Object> markerProjectLocks = new HashMap<>();
  private DeltaProcessor deltaProcessor = new DeltaProcessor(this);

  public IResourceChangeListener getResourceChangeListener() {
    return deltaProcessor;
  }
  
  public static Object getLockObject(IProject p){
    Object l = markerProjectLocks.get(p);
    if(l==null){
      l = ResManager.getManager().getTracker().getLocalName(p);
      markerProjectLocks.put(p, l);
    }
    return l;
  }


  public DeltaProcessor getDeltaProcessor() {
    return deltaProcessor;
  }

  public ResourceStorage(IProject project) {
    this.project = project;
    Lock wl = writeLock(null);
    try{
      IFile file = project.getFile(ROOT_NODE_FILENAME);
      if (file.exists()) {
        //if (!resourceMap.inCache(file)) {
        rootNodeUUID = getFileUUID(file, false); //resourceMap.cachePut(file, uuid);
        //            }
        //            rootNodeUUID = resourceMap.cacheGet(file);
      }
    }
    catch(Exception e){
      e.printStackTrace();
    }finally{
      wl.unlock();
    }
    try {
      initCaches(this, CacheInitializationStage.STORAGE_INIT, getMonitor());
    } catch (Exception e1) {
      RLogger.logError(e1);
    }
    try {
      IFile f = project.getFile(CoreUtils.CACHE_FOLDER_NAME);
      if(f!=null && f.exists())
        f.refreshLocal(IResource.DEPTH_INFINITE, null);
    } catch (CoreException e) {
      e.printStackTrace();
    }
  }
  
  public IFolder getResourceFolder(UUID uuid) {
    Lock rl = readLock(uuid);
    IFolder resFolder = null;
    try{
      String id = getNodeId(uuid);
      IFile file = null;
      try {
        file = getResource(uuid, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      if (file == null) {
        return null;
      } else {
        resFolder = file.getParent().getFolder(new Path(id + RESOURCE_SUFFIX));
        return resFolder;
      }
    }finally{
      //boolean rl = true,wl = false;
      try{
        rl.unlock();
//        if (resFolder!=null && !resFolder.isSynchronized(1)){
//          rl = false;
//          writeLock();
//          wl = true;
//          try {
//            resFolder.refreshLocal(1, null);
//          } catch (CoreException e) {
//            e.printStackTrace();
//          }
//        }
      }finally{
//        if(rl)
//          readUnlock(uuid);
//        if(wl)
//          writeUnlock(uuid);
      }
    }
  }

  @Override
  public UUID getRootNode() {
    synchronized (this) {
      return rootNodeUUID;
    }
  }

  @Override
  public String getNodeId(UUID uuid) {
    if (uuid == null) return null;
    Lock rl = readLock(uuid);
    try{
      if (uuid.equals(getRootNode())) {
        return "/";
      }
      java.nio.file.Path fid = null;
      try {
        fid = getResourcePath(uuid, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      if (fid == null) {
        return null; // node not found;
      }
      return CoreUtils.getFileName(fid, false);
    }
    finally{
      rl.unlock();
    }
  }

  @Override
  public Map<String, AttributeDefinition> getStorageAttributes(UUID uuid) {
    Lock rl = readLock(uuid);
    try{
      if(activeAttributes.containsKey(uuid))
        return activeAttributes.get(uuid);
      IFile file = null;
      try {
        file = getResource(uuid, LockType.LOCK_WAIT);
      } catch (LockIsBusy e1) {
        e1.printStackTrace();
      }
      if (file == null) return new HashMap<>();
      InputStream nodeSource = readFile(file);
      IFolder folder = getResourceFolder(uuid);
      TempFolderFilesProvider fileFolder = null;
      if(folder!=null) {
        IPath p = folder.getLocation();
        if(p!=null)
          fileFolder = TempFolderFilesProvider.getOne(p.toFile(),true);
        else
          fileFolder = TempFolderFilesProvider.getNewNotInited(AttributeType.HTML);
      }else
        fileFolder = TempFolderFilesProvider.getNewNotInited(AttributeType.HTML);
      try {
        Map<String, AttributeDefinition> mp = NodeMapper.getAttributes(uuid, nodeSource, 
          fileFolder);
        if(mp!=null)
          activeAttributes.put(uuid, mp);
        return mp;
      } 
      catch (IOException | JSONException e) {
        RLogger.logError(e);
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        Map<String, AttributeDefinition> result = new HashMap<>();
        result.put(
            TreeNode.ATTR_DESCRIPTION,
            new AttributeDefinition(uuid, TreeNode.ATTR_DESCRIPTION, AttributeAvailability.LOCAL,
                new HtmlValueProvider(AttributeType.HTML, stackTrace.toString(),
                  TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),Flag.HIDDEN));
        result.put(
            TreeNode.ATTR_TYPE,
            new AttributeDefinition(uuid, TreeNode.ATTR_TYPE, AttributeAvailability.LOCAL,
                new ConstValueProvider(AttributeType.STRING, ErrorNode.TYPE_NAME),Flag.HIDDEN));
        activeAttributes.put(uuid, result);
        return result;
      }
      finally{
        if(nodeSource == null)
          RLogger.logError("Problems while accessing file for "+uuid+" during saveAttrs. Not exists or access error "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
        else
          try {
            nodeSource.close();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      }
    }finally{
      rl.unlock();
    }
  }

  @Override
  public void clearCaches(boolean del) {
    //writeLock(null);
    try{
      activeAttributes.clear();
      resinited = false;
      if(!del)
        clearProblems();
      //rs.initRoot();
    }finally{
      //writeUnlock(null);
    }
    super.clearCaches(del);
  }

  @Override
  public UUID getParent(UUID uuid) {
    //readLock(uuid);
    try{
      java.nio.file.Path relPath = null;
      try {
        relPath = getResourcePath(uuid, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        e.printStackTrace();
      }
      if (relPath == null) {
        return null; // node not found
      }
      return getParent(relPath);
    }finally
    {
      //readUnlock(uuid);
    }
  }

  public void clearPersistent(UUID uuId) {
    //getCacheStorage().clearCachedCache(uuId.toString());
    //TreeDB.getCPCacheStorage().clearCachedCache(uuId.toString());
    synchronized (getLockObject(getProject())) {//TODO it it necessary?
      IFile file = null;
      try {
        file = getResource(uuId, LockType.NO_LOCK);
      } catch (LockIsBusy e1) {
        e1.printStackTrace();
      }
      if (file == null) return;
      if (file.exists() && file.isAccessible()) {
        try {
          file.deleteMarkers(null, false, IResource.DEPTH_INFINITE);
        } catch (CoreException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public UUID getParent(final java.nio.file.Path relPath) {
    //readLock(null);//TODO
    try{
//      if (file.getParent().getType() == IResource.PROJECT) {
//        return null;
//      }
//      String folderName = file.getParent().getName();
//      String parentId = folderName.substring(0, folderName.length());
//  
      java.nio.file.Path parPath = CoreUtils.getJsonPathParentJsonPath(relPath);
      //String parentP = CoreUtils.getFileParentFile(file);
      //if(parentP == null)
        //return null;
      return getUUId(parPath, null, false, true);
//      IFile parentResource;
//      IContainer parentFolder = file.getParent().getParent();
//      parentResource = parentFolder.getFile(new Path(parentId + ".json"));
//      if (!parentResource.exists()) {
//        return getResourceMap().cacheGet(toRPath(parentResource));
//      }
//      return getUUId(parentResource);
    }
    finally{
      //readUnlock(null);
    }
  }

  public static java.nio.file.Path toRelativePath(IResource resource) {
    if(resource == null)
      return null;
    return java.nio.file.Path.of(resource.getProjectRelativePath().toString());
  }


  public boolean inCache(IFile f) {
    return getResourceMap().inCache(toRelativePath(f));
  }

  public UUID getUUId(final IFile file) {
    return getUUId(file, false, true);
  }

  public UUID getUUId(final java.nio.file.Path relativePath, IFile file, boolean legacyMode, boolean withcache) {
    UUID uid = null;
    if(relativePath == null)
      return null;
    if(withcache)
      try {
        uid = getResourceMap().cacheGet(relativePath);
      } catch (LockIsBusy e) {
      }
    if (!withcache || uid==null) {
      try{
        if(withcache)
          getResourceMap().writeLock();
        if(file == null)
          file = project.getFile(relativePath.toString());
        //UUID uid = null;
        uid = getFileUUID(file, legacyMode);
        if (uid != null) {
          if(withcache)
            getResourceMap().cachePut(getFullPath(relativePath), relativePath, uid, false, -1);
          //if(!resourceMap.haveCache(file, uid)){
          //}
        }
        return uid;
      }finally{
        if(withcache)
          getResourceMap().writeUnlock();
      }
    } else {
      return uid;
    }
  }
  public UUID getUUId(final IFile file, boolean withcache) {
    return getUUId(file, false, withcache);
  }
  public UUID getUUId(final IFile file, boolean legacyMode, boolean withcache) {
    String ext = file.getFileExtension();
    if (ext == null || !ext.contains("json")) return null;
    java.nio.file.Path path = toRelativePath(file);
    return getUUId(path, file, legacyMode, withcache);
  }

  @Override
  public UUID getStorageUUId(java.nio.file.Path relativePath, boolean legacyMode) {
    return getUUId(project.getFile(relativePath.toString()), legacyMode, false);//TODO test
  }
  
  Stack<Object[]> toup = new Stack<>();

  public UUID getFileUUID(IFile file, boolean legacyMode) {
    if(!file.exists())
      return null;
    try(InputStream content = readFile(file)){
      if (content == null){
        RLogger.logInfo("Empty file "+file.getFullPath().toString());
        return null;
      }
      return legacyMode ? LegalicyNodeMapper.getUUID(content) : NodeMapper.getUUIdExc(content);
    } catch (JSONException e) {
      RLogger.logError("Cannot parse content of file "+file.getFullPath()+" (json parse error)");
      RLogger.logDebug(CoreUtils.getTrace(e), 7);
      return null;
    }catch (IOException e) {
      RLogger.logError("Cannot parse "+file.getFullPath()+" (file access error or json error)");
      RLogger.logDebug(CoreUtils.getTrace(e), 7);
      return null;
    }
    finally{
    }
  }

  public Collection<UUID> getChildren(IFile parentFile, UUID uid) {
    Collection<UUID> result = Collections.emptySet();
    if (parentFile == null) {
      return result; // node not found
    }
    String nodeId = parentFile.getName();
    nodeId = nodeId.substring(0, nodeId.length()-5);
    if(nodeId == null || nodeId.isEmpty())
      return result;
    if (nodeId.equals("/")) {
      nodeId = "root";
    }

    IFolder childrenFolder = parentFile.getParent().getFolder(Path.fromOSString(nodeId));

    if (!childrenFolder.exists()) {
      return result;
    }

    try {
      for (IResource res : childrenFolder.members()) {
        if (res.getType() == IResource.FILE && res.getName().toLowerCase().endsWith(".json")) {
          if (res.exists()) {
            try {
              UUID childUUID = getFileUUID((IFile) res, false);
              if (childUUID != null) {
                if(result.isEmpty())
                  result = new HashSet<>();
                if(childUUID.equals(uid)) {
                  RLogger.logError("Node cannot be added to itself");
                }else
                  result.add(childUUID);
              }
            } catch (Exception e) {
              e.printStackTrace(); //cannot load
            }
          }
        }
      }
    } catch (CoreException e) {
      e.printStackTrace();
    }
    return result;
  }
  @Override
  public Collection<UUID> getChildren(UUID uid) {
    IFile parentFile = null;
    try {
      parentFile = getResource(uid, LockType.LOCK_WAIT);
    } catch (LockIsBusy e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    return getChildren(parentFile, uid);
  }
  @Override
  public boolean canLoad(UUID uuid) {
    return getResourceMap().inCache(uuid); //&&resourceMap.getResource(uuid).isAccessible();
  }

  public static InputStream readFile(IFile file) {
    try {
      String fileExt = file.getFileExtension();
      if (fileExt == null || !fileExt.equalsIgnoreCase("json") || !file.exists()) {
        return null;
      }
      return file.getContents(true);
    } catch (CoreException e) {
      e.printStackTrace();
    }
    return null;
  }
  public static String readFile(InputStream is) {
    /*  if (!file.isSynchronized(IResource.DEPTH_ZERO)||!file.isAccessible()) {
        // TODO: add monitor
        file.refreshLocal(IResource.DEPTH_ZERO, null);
    }*/
    StringBuilder jsonsb = new StringBuilder();
    if(is == null)
      return null;
    try(Scanner s = new Scanner(is, "UTF-8")){
      while (s.hasNextLine()) {
        jsonsb.append(s.nextLine().trim());
        jsonsb.append("\n");
      }
    }
    return jsonsb.toString();
  }
  public static String readFile(IStreamContentAccessor file) {
    try (InputStream is = file.getContents()){
      return readFile(is);
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e1) {
      RLogger.logError(e1);
  }
    return null;
  }
  @Override
  public URI getNodeResourcePath(UUID uuId) {
    IFolder folder = getResourceFolder(uuId);
    if (folder == null) return null;
    //TODO look into
    return folder.getLocationURI();
  }

  public IProject getProject() {
    return project;
  }

  @Override
  public InputStream getResourceContents(UUID uuid, String resName) {
    try {
      // TODO why we lost sync here?
      IFolder resFolder = getResourceFolder(uuid);
      if (resFolder == null || !resFolder.exists()) return null;
      //if (!resFolder.isSynchronized(1)) {
      //}
      File f = resFolder.getLocation().toFile().toPath().resolve(resName).toFile();
      if (f == null || !f.exists()) return null;
      return new FileInputStream(f);
    } catch (FileNotFoundException e) {
    RLogger.logError(e);
  }
    return null;
  }

  @Override
  public boolean isResourceExists(UUID uuid, String resName) {
    if (resName == null) return false;
    IFolder resFolder = getResourceFolder(uuid);
    if (resFolder == null || !resFolder.exists()) return false;
    File f = resFolder.getLocation().toFile();
    return f.toPath().resolve(resName).toFile().exists();
  }

  private void recursiveResourceList(IFolder folder, String path, Set<String> result)
      throws CoreException {
    if (folder.getName().startsWith(".svn")) return;
    for (IResource res : folder.members()) {
      // use unix path separator
      String curPath = (path.isEmpty()?"":(path + "/")) + res.getName();
      if (res instanceof IFolder) {
        recursiveResourceList((IFolder) res, curPath, result);
      } else {
        result.add(curPath);
      }
    }
  }

  @Override
  public Set<String> listResources(UUID uuid) {
    Set<String> result = new HashSet<>();
    try {
      IFolder ifld = getResourceFolder(uuid);
      if (ifld != null && ifld.exists()) {
        recursiveResourceList(ifld, "", result);
      }
    } catch (CoreException e) {
      e.printStackTrace();
    }
    return result;
  }

  @Override
  public String getLocalName() {
    return ResManager.getManager().getTracker().getLocalName(project);
  }
  @Override
  public String getVisibleName() {
    return getProjectName();
  }
  @Override
  public String getProjectName() {
    return EclipseTracker.getProjectName(project);
  }

  private IFile getFile(UUID node, UUID parentUUId, String id, boolean create, IProgressMonitor monitor) {
    IFile file = null;
    UUID root = getRootNode();
    if (parentUUId == null && (root == null || getRootNode().equals(node)))
      file = project.getFile(ROOT_NODE_FILENAME);
    else
      if (id == null)
        throw new RuntimeException ("Id is null on getFile!");
    if(parentUUId!=null&&id!=null){
      IContainer childFolder = getChildrenFolder(parentUUId, null);
      if (childFolder == null) return null;
      if(childFolder instanceof IFolder)
        file = ((IFolder)childFolder).getFile(id + ".json");
      else if (childFolder instanceof IProject)
        file = ((IProject)childFolder).getFile(id + ".json");
    }
    return file;
  }

  private IContainer getChildrenFolder(UUID parentUUId, IProgressMonitor monitor) {
    if (parentUUId == null) {
      return project; //root
    } else {
      IFile pfile = getFile(parentUUId);
//      // get parent id
//      String parentId = getNodeId(parentUUId, true);
      if (pfile == null) {
        RLogger.logInfo("parid null for "+parentUUId);
      } else {
//        if ("/".equals(parentId)) {
//          parentId = "root";
//        }
        IFolder childFolder;
        //IFile parentFile = getFile(parentUUId);
        String name = pfile.getName();
        if (pfile.getParent().getType() == IResource.PROJECT) {
          childFolder = ((IProject) pfile.getParent()).getFolder(name.substring(0,name.length()-5));
        } else {
          childFolder = ((IFolder) pfile.getParent()).getFolder(name.substring(0,name.length()-5));
        }
        //IWorkspace works=ResourcesPlugin.getWorkspace();
        //  if(works!=null)
        //  works.checkpoint(false);
        return childFolder;
      }
    }
    return null;
  }
  ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  private ConcurrentMap<UUID, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
  //final LockProxy<ReentrantLock> rl = new LockProxy<>(new ReentrantLock());
  
  public void writeUnlock(UUID uid){
    if(uid == null)
      rwl.writeLock().unlock();
    else{
      getRWL(uid).writeLock().unlock();
    }
  }
  @Override
  public Lock writeLock(UUID uid){
    if(uid == null) {
      Lock wl = rwl.writeLock(); 
      wl.lock();
      return wl;
    }else{
      Lock wl = getRWL(uid).writeLock();
      wl.lock();
      return wl;
    }
      
  }
  private ReentrantReadWriteLock getRWL(UUID uid) {
    ReentrantReadWriteLock rl = locks.get(uid);
    if(rl == null){
      rl = new ReentrantReadWriteLock();
      locks.put(uid, rl); 
    }
    return rl;
  }

  Lock readLock(UUID uid){
    if(uid == null) {
      Lock rl = rwl.readLock(); 
      rl.lock();
      return rl;
    }else{
      Lock rl = getRWL(uid).readLock();
      rl.lock();
      return rl;
    }
  }
  void readUnlock(UUID uid){
    if(uid == null) 
      rwl.readLock().unlock();
    else
      getRWL(uid).readLock().unlock();
  }
  @Override
  public void createStorageNode(
      NodeDescSet nds, String template, byte flag) {
    IStorageDeltaListener dnt = getDeltaTarget();
    UUID ruuid = nds.root.getUUId();
    UUID rparentUUId = nds.root.getWorkParentUUID();
    if(rparentUUId == null && rootNodeUUID == null)
      rootNodeUUID = ruuid;
    Map<UUID, Map<String, AttributeDefinition>> opAttrs = new HashMap<>();
    Map<UUID, IFile> opFile = new HashMap<>();
    Map<UUID, java.nio.file.Path> opParent = new HashMap<>();
    try{
      if (dnt != null)
        dnt.beforeCreated(nds, template, flag);
      LinkedList<NodeDesc>alnds = nds.allNodesCreate;
      ListIterator<NodeDesc> rev = alnds.listIterator();
      ResourceMap rm = getResourceMap();
      URI loc = getLocation();
      java.nio.file.Path path = new File(loc).toPath();
      boolean cacheInited = rm.getCacheInitationState(getCMM().getSettingsManipulator(), path)!=CacheManipulatorInitializationStage.NOT_INITED;
      while(rev.hasNext()) {
        NodeDesc nd = rev.next();
        String id = nd.getId();
        UUID uuid = nd.getUUId(),
            parentUUId = nd.getWorkParentUUID();
        IFile ffile = getFile(uuid, parentUUId, id, true, null);
        IFile file = ffile;
        if (file == null)
          continue;
        java.nio.file.Path filep = java.nio.file.Path.of(file.getProjectRelativePath().toString());
        opFile.put(uuid, file);
        opParent.put(uuid, filep);
        boolean wu = false;
        try {
          if(cacheInited) {
            rm.writeLock();
            wu = true;
            try{
              rm.cachePut(java.nio.file.Path.of(file.getLocation().toString()), filep, uuid, true,-1);
            }
            finally{
              if(wu){
                rm.writeUnlock();
                wu = false;
              }
            }
            //IFile file = getFile(parentUUId, id, true, monitor);
            rm.writeLock();
            wu = true;
          }
          //getResourceMap().cachePut(file, uuid, false,true);
          //if (file == null) return;
          //without bottom line doc can have null id on import
          Map<String, AttributeDefinition> attrs = new HashMap<>(nd.getAttributes());
          opAttrs.put(uuid, attrs);
          activeAttributes.put(uuid, attrs);
          if(cacheInited) {
            //              resourceMap.toLook.add(file);
            getResourceMap().cachePut(java.nio.file.Path.of(file.getLocation().toString()), filep, uuid, true,-1);
            if(wu){
              wu = false;
              rm.writeUnlock();
            }
          }
        }
        finally {
          if(wu)
            rm.writeUnlock();
        }
      }
      String trace = CoreUtils.getTrace(Thread.currentThread().getStackTrace());

      IWorkspace works = ResourcesPlugin.getWorkspace();
      IResourceRuleFactory ruleFactory = works.getRuleFactory();
      ISchedulingRule rule = ruleFactory.modifyRule(project);
      IContainer par = getChildrenFolder(rparentUUId, null);
      if(par!=null)
        rule = MultiRule.combine(rule, ruleFactory.createRule(par));
      final ISchedulingRule frule = rule;
      IWorkspaceRunnable createJob =
          new IWorkspaceRunnable() {
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {
              try {
                LinkedList<NodeDesc>alnds = nds.allNodesCreate;
                ListIterator<NodeDesc> rev = alnds.listIterator();
                while(rev.hasNext()) {
                  NodeDesc nd = rev.next();
                  String id = nd.getId();
                  UUID uuid = nd.getUUId();
                  Map<String, AttributeDefinition> attrs = opAttrs.get(uuid);
                  UUID parentUUId = nd.getWorkParentUUID();
                  final IContainer pfldr = getChildrenFolder(parentUUId, null);
                  IFile file = opFile.get(uuid);
                  if (file == null) 
                    continue;
                  java.nio.file.Path filep = opParent.get(uuid);
                  try {
                    if (pfldr != null && !pfldr.exists() && (pfldr instanceof IFolder))
                      try {
                        ((IFolder)pfldr).create(true, true, monitor);
                      } catch (CoreException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                      }
                  } finally {
                  }
                  boolean wu = false;
                  try {
                    String jsonString = NodeMapper.toJSON(uuid, mapToList(attrs),true).toString(4);
                    ByteArrayInputStream in = new ByteArrayInputStream(jsonString.getBytes("UTF-8"));
                    //TODO wrong?
                    //                  IWorkspace works=ResourcesPlugin.getWorkspace();
                    //                  if(works!=null)
                    //                      works.checkpoint(false);
                    //s
                    if (file.exists()) {
                      file.setContents(in, true, true, monitor);
                    } else
                      try {
                        file.create(in, true, monitor);
                      } catch (CoreException re) {
                        try {
                          file.setContents(in, true, true, monitor);
                        } catch (CoreException re2) {
                          re2.printStackTrace();
                        }
                      }
                    try {
                      //file = getFile(uuid, parentUUId, id, true, monitor);
                      if (!file.getParent().exists())
                        continue;
                      if (file.exists())
                        if (!file.getCharset().toUpperCase().equals("UTF-8")) {
                          try {
                            file.refreshLocal(0, monitor);
                            file.setCharset("UTF-8", monitor);
                          } catch (CoreException ce) {
                            ce.printStackTrace();
                          }
                        }
                    } finally {
                    }
                    //тут поменяно местамиs
                    //   synchronized (ResourceStorage.this) {
                    // }
                  } catch (UnsupportedEncodingException e) {
                    RLogger.logError(e);
                    RLogger.logError(trace);
                  } catch (JSONException e) {
                    RLogger.logError(e);
                    RLogger.logError(trace);
                  } catch (Exception e) {
                    RLogger.logError(e);
                    RLogger.logError(trace);
                  } finally {
                    if(wu){
                      wu = false;
                      getResourceMap().writeUnlock();
                    }
                    if(file !=null && (!file.exists()||isEmpty(file))){
                      RLogger.logError("File "+file.getFullPath()+" is empty after creation. Trace:\n"+trace);
                    }
                  }
                }
                IWorkspace works = ResourcesPlugin.getWorkspace();
                works.checkpoint(false);
              }finally {
               
              }
            }
          };
      runWithoutDeltaProc(
         createJob,
         rule);
    }
    finally{
       //returns back non-job version to avoid concurrent problems on caches
      try {
        if (dnt != null) 
          dnt.created(nds, template, flag);
      } finally {
      }
    }
  }

  protected static boolean isEmpty(IFile file) {
    InputStream is = null;
    try {
      is = file.getContents();
    } catch (CoreException e2) {
      e2.printStackTrace();
    }
    if(is == null)
      return true;
    try{
      return is.available()==0;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        is.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return true;
  }


  @Override
  public void changeNodeId(final UUID uuid, final String id, byte flag) {
    Lock wl = writeLock(uuid);
    try{
      IWorkspace works = ResourcesPlugin.getWorkspace();
      IResourceRuleFactory ruleFactory = works.getRuleFactory();
      IStorageDeltaListener dnt = getDeltaTarget();
      IWorkspaceRunnable changeNodeId =
          new IWorkspaceRunnable() {
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {
              byte l = 0;
              try{
                l = 1;
                getResourceMap().readLock();
                String nodeId = getNodeId(uuid);
                if (id.equals(nodeId)) {
                  //strange
                  return;
                }
                IFile nodeFile = null;
                try {
                  nodeFile = getResource(uuid, LockType.NO_LOCK);
                } catch (LockIsBusy e1) {
                  e1.printStackTrace();
                }
                if (nodeFile == null || nodeFile.getParent() == null) {
                  return;
                }
                l = 0;
                getResourceMap().readUnlock();
                l = 2;
                getResourceMap().writeLock();
                IPath newPath = nodeFile.getParent().getFullPath();
    
                newPath = newPath.addTrailingSeparator();
                IPath newFilePath = newPath.append(id + ".json");
                if (nodeFile.getFullPath().equals(newFilePath)) 
                  return;
                try {
                  nodeFile.move(newFilePath, true, monitor);
                } catch (Exception e) {
                  e.printStackTrace();
                }
                IFolder folder =
                    project.getFolder(
                        newPath
                            .append(nodeId + RESOURCE_SUFFIX)
                            .makeRelativeTo(project.getFullPath()));
                if (folder.exists()) {
                  folder.move(newPath.append(id + RESOURCE_SUFFIX), true, monitor);
                }
                if(!nodeId.isEmpty()) {//.json tends not to have children (it is actually broken)
                //but we may wish to rename it
                  folder =
                    project.getFolder(newPath.append(nodeId).makeRelativeTo(project.getFullPath()));
                  if (folder.exists()) {
                    try {
    
                      folder.move(newPath.append(id), true, monitor);
    
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                  }
                }
                IFile newFile = project.getFile(newFilePath.makeRelativeTo(project.getFullPath()));
                //                if (!newFile.getCharset().equals("UTF-8")) {
                //                  newFile.setCharset("UTF-8", monitor);
                //                }
                CacheStorage cs = getCacheStorage();
                CacheTransactionDescription r = cs.startCacheTransaction("alter cache on node move", 2);
                try {
                  getResourceMap().cacheReplace(toRelativePath(nodeFile), toRelativePath(newFile), uuid);
                  getResourceMap()
                    .updatePath(//TODO test
                        toRelativePath(nodeFile.getParent()).resolve(nodeId),
                            toRelativePath(newFile.getParent()).resolve(id));
                }finally {
                  if(r != null && r.error == null)
                    cs.commit(r);
                }
              IFile file = nodeFile;
              if (!file.getParent().exists()) return;
              if (file.exists())
                if (!file.getCharset().toUpperCase().equals("UTF-8")) {
                  try {
                    file.refreshLocal(0, monitor);
                    file.setCharset("UTF-8", monitor);
                  } catch (CoreException ce) {
                    ce.printStackTrace();
                  }
                }
                
                IWorkspace works = ResourcesPlugin.getWorkspace();
                works.checkpoint(false);
              }
              catch(Exception e){
                e.printStackTrace();
              }
              finally{
                if(l==1)
                  getResourceMap().readUnlock();
                if(l==2)
                  getResourceMap().writeUnlock();
                
              }
              
            }
          };
      waitAccess(project);
      String old = getNodeId(uuid);
      runWithoutDeltaProc(
          changeNodeId,
          MultiRule.combine(
              ruleFactory.modifyRule(project),
              ruleFactory.moveRule(getFile(uuid), getFile(uuid).getParent())));
    }finally{
      wl.unlock();
    }
    
  }
  public ISchedulingRule appendRule(IResourceRuleFactory ruleFactory, DeleteNodeResources<IResource, IResource> dnr, ISchedulingRule old) {
    if(dnr.json!=null) {
      if(old == null)
        old = ruleFactory.deleteRule(dnr.json.getRaw());
      else
        old = MultiRule.combine(
            old,
            ruleFactory.deleteRule(dnr.json.getRaw()));
    }
    if(dnr.children!=null)
      old = MultiRule.combine(
            old,
            ruleFactory.deleteRule(dnr.children.getRaw()));
    if(dnr.resources!=null)
      old = MultiRule.combine(
            old,
            ruleFactory.deleteRule(dnr.resources.getRaw()));
    return old;
  }
  StorageDeleteOperation<IResource, IResource> sdo = null;
  @Override
  public void remove(final NodeDescSet nd, byte flag) {
    ResourceMap rm = getResourceMap(); 
    IFile ares = null;
    rm.readLock();
    try{
      ares = getResource(nd.root.getUUId(),LockType.NO_LOCK);
    }
    catch (LockIsBusy e) {
      e.printStackTrace();
    }
    finally{
      rm.readUnlock();
    }
    if(ares==null) {
      //cache miss, needs to check actual parent child
      try {
        IFile resPar = getResource(nd.root.getWorkParentUUID(),LockType.NO_LOCK);
        if(resPar!=null)
        {
          String jsonPart = resPar.getName();
          jsonPart = jsonPart.substring(0, jsonPart.length()-5);//-.json
          IResource res = resPar.getParent().findMember(jsonPart);
          if(res!=null && res instanceof IContainer) {
            ares = (IFile) ((IContainer)res).findMember(nd.root.getId()+".json");
          }
        }
      } catch (LockIsBusy e) {
        e.printStackTrace();
      }
    }
//    if(ares == null || !ares.exists())
//      тут нужно событий запустить
//      return;
    IContainer childsCont = null, resourcesCont = null;
    if(ares!=null) {
      String aresName = ares.getName();
      String aname = aresName.substring(0, aresName.length()-5);
      IResource temp = ares.getParent().findMember(aname);
      if(temp!=null && temp instanceof IContainer)
        childsCont = (IContainer) temp;
      temp = ares.getParent().findMember(aname+RESOURCE_SUFFIX);//TODO replace by const
      if(temp!=null && temp instanceof IContainer)
        resourcesCont = (IContainer) temp;
    }
    sdo = new StorageDeleteOperation<IResource, IResource>(){
        @Override
        public Object formSchelduleRule(java.util.concurrent.ConcurrentLinkedDeque<DeleteNodeResources<IResource, IResource>> deleteQueue) {
          IWorkspace works = ResourcesPlugin.getWorkspace();
          works.checkpoint(false);
          IResourceRuleFactory ruleFactory = works.getRuleFactory();
          
          DeleteNodeResources<IResource, IResource> dnr = deleteQueue.getLast();//first added
          ISchedulingRule mr = null;
          IContainerAble<IResource> removePar = null;
          if(dnr.parent!=null && dnr.parent.members().length == dnr.getResourcesCount()){//TODO mb not working when git was in a project
            removePar = dnr.parent;
            mr = ruleFactory.deleteRule(removePar.getRaw());
          }
          
          for(DeleteNodeResources<IResource,IResource> dnrit:deleteQueue) 
            mr = appendRule(ruleFactory, dnrit, mr);
          return mr;
        }

        @Override
        public void startDeleteJob(Object rule, ProgressRunnable pr) {
          
          IWorkspaceRunnable deleteJob = //exact method to delete 
            new IWorkspaceRunnable() {
              @Override
              public void run(IProgressMonitor monitor) throws CoreException {
                pr.run(new EclipseProgressMonitor(monitor));
              }
            };
          runWithoutDeltaProc(deleteJob, (ISchedulingRule) rule);
        }

        @Override
        public void startProjectScopedJob(String name, ProgressRunnable pr, Object project) {
          IWorkspaceRunnable deleteJob = //starting runnable job 
              new IWorkspaceRunnable() {
                @Override
                public void run(IProgressMonitor monitor) throws CoreException {
                  pr.run(new EclipseProgressMonitor(monitor));
                }
              };
            runWithoutDeltaProc(deleteJob, (ISchedulingRule) project);
        };
    };
    //TODO add to monitor?
    sdo.delete(getDeltaTarget(), rm, nd, toFileAble(ares), toContainerAble(childsCont), toContainerAble(resourcesCont), new IContainerAbleRefresher() {
      
      @Override
      public void refreshRecoursive(IContainerAble cont) {
        IContainer c = (IContainer) cont.getRaw();
        try {
          c.refreshLocal(IResource.DEPTH_INFINITE, null);
        } catch (CoreException e) {
          RLogger.logError(e);
        }
      }
    }, activeAttributes, ares!=null?ares.getProject():null, flag);
  }

  private static IResourceAble[] EMPTY = new IResourceAble[0];
  private IContainerAble<IResource> toContainerAble(IContainer resourcesCont) {
    if(resourcesCont == null)
      return null;
    return new IContainerAble<IResource>(){

      @Override
      public IResource getRaw() {
        return resourcesCont;
      }

      @Override
      public String getName() {
        return resourcesCont.getName();
      }
      @Override
      public IContainerAble<IResource> getParent() {
        IContainer temp = resourcesCont.getParent();
        if(temp!=null)
          return toContainerAble(temp);
        return null;
      }
      @Override
      public void waitAccess() {
        ResourceStorage.waitAccess(resourcesCont);
      }

      @Override
      public void delete(boolean b, com.unitesk.requality.core.operation.IProgressMonitor monitor) {
        try {
          IProgressMonitor epm = ((EclipseProgressMonitor)monitor).getRaw();
          resourcesCont.refreshLocal(IResource.DEPTH_INFINITE, epm);
          if(resourcesCont.exists()) 
            resourcesCont.delete(b, epm);
        } catch (CoreException e) {
          RLogger.logError(e);
        }
      }
      private IResourceAble[] childs = null;
      @Override
      public IResourceAble[] members() {
        if(childs == null) {
          IResource[] res = null;
          try {
            res = resourcesCont.members();
          }catch (CoreException e) {//can not exist
          }
          if(res == null || res.length == 0)
          {
            childs = EMPTY;
            return EMPTY;
          }
          childs = new IResourceAble[res.length];
          int i = 0;
          for(IResource r:res)
          {
            if(r instanceof IContainer)
              childs[i++] = toContainerAble((IContainer) r);
            else
              childs[i++] = toFileAble((IFile) r);
          }
          return childs;
        }
    return childs;
      }

 
    };
  }

  private IFileAble<IResource, IResource> toFileAble(IFile ares) {
    if(ares == null)
      return null;
    return new IFileAble<IResource, IResource>() {
      
      @Override
      public IResource getRaw() {
        return ares;
      }
      
      @Override
      public IContainerAble<IResource> getParent() {
        return toContainerAble(ares.getParent());
      }
      
      @Override
      public String getName() {
        return ares.getName();
      }

      @Override
      public java.nio.file.Path toRelativePath() {
        return ResourceStorage.toRelativePath(ares);
      }

      @Override
      public void waitAccess() {
        ResourceStorage.waitAccess(ares);
      }

      @Override
      public void delete(boolean b, com.unitesk.requality.core.operation.IProgressMonitor monitor) {
        try {
          IProgressMonitor epm = ((EclipseProgressMonitor)monitor).getRaw();
          ares.refreshLocal(IResource.DEPTH_INFINITE, epm);
          if(ares.exists())
            ares.delete(b, ((EclipseProgressMonitor)monitor).getRaw());
        } catch (CoreException e) {
          RLogger.logError(e);
        }
      }
    };
  }

  private static Collection<AttributeDefinition> mapToList(Map<String, AttributeDefinition> map) {
    if (map == null) return null;
    List<AttributeDefinition> attrs = new LinkedList<>();
    for (Entry<String, AttributeDefinition> s:map.entrySet())
    //attrs.addAll(map.values());
    {
      if (s.getValue()!=null) attrs.add(s.getValue());
    }
    return attrs;
  }

  @Override
  public void saveStorageAttributes(
      final UUID uuid, final ChangesSet<Attribute> changes, byte flag) {
    boolean cont = false;
    for (String k:changes.getAll()){
      Attribute a = changes.getOld(k),
                b = changes.getNew(k);
      if(a!=null && !a.isVirtual() && a.getOrigin().equals(uuid)||b!=null && !b.isVirtual()&&b.getOrigin().equals(uuid))
        cont = true;
    }
    if(!cont)
      return;
    boolean wlo = false;
  //ISchedulingRule rule = project;
    waitAccess(project);
    
    IWorkspace works = ResourcesPlugin.getWorkspace();
    IResourceRuleFactory ruleFactory = works.getRuleFactory();
    waitAccess(project);
    Lock wl = writeLock(uuid);
    try{
      final IContainer parf = getChildrenFolder(getParent(uuid), null);
      String trace = CoreUtils.getTrace(Thread.currentThread().getStackTrace());
      IFile file = null;
      try {
        file = getResource(uuid, LockType.LOCK_WAIT);
      } catch (LockIsBusy e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      if (file == null) 
        return;
      ResourceMap rm = getResourceMap();
      
      rm.writeLock();
      wlo = true;
      Map<String,AttributeDefinition> old = null;
      try {
        old = getStorageAttributes(uuid);//TODO may require some time
      }catch(Exception e) {
        RLogger.logInfo("Cannot load old attributes during saving "+uuid);
      }
      if(old == null)
        old = new HashMap<>();
      Map<String, Attribute> newA = changes.getNewMap();
      for(Entry<String, Attribute> en:newA.entrySet()){
        Attribute a = en.getValue();
        if(a == null || a.getValue() == null)
          old.remove(en.getKey());
        else
          old.put(en.getKey(),a.getDefinition());
      }
      activeAttributes.put(uuid, new HashMap<>(old));

      if(old.get(TreeNode.ATTR_TYPE).getRawValue().getValue().equals(ErrorNode.TYPE_NAME)){
        RLogger.logDebug("Attempted to save corrupted node "+uuid,2);
        return;//cannot save error node attrs
      }
      java.nio.file.Path fileP = toRelativePath(file);
      java.nio.file.Path rpath = getResourceMap().getResourcePath(uuid);
      if (!fileP.equals(rpath))
      {
        if(rpath.toString().startsWith("./"))
          rpath = rpath.subpath(1, rpath.getNameCount());
        if (!fileP.equals(rpath)){
          getResourceMap().cachePut(file.getLocation().toFile().toPath(), fileP, uuid,  true,-1);
          RLogger.logError("Saving attributes for path "+fileP+" for incorrect uuid, uuid map rewrited");
        }
      }
      try {
        file = getResource(uuid, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      finally {
        if(wlo)
            rm.writeUnlock();
      }
      final Map<String,AttributeDefinition> fold = old;
      final IFile ffile = file;
      IWorkspaceRunnable saveAttributes =
          new IWorkspaceRunnable() {
            @Override
            public void run(IProgressMonitor monitor) {
              IFile file = ffile;
              Map<String,AttributeDefinition> old = fold;
              String jsonString;
              try {

                if(!file.isSynchronized(0))
                  try {
                    file.refreshLocal(0, null);
                  } catch (CoreException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                  }
                              //RLogger.logInfo(file.getFullPath());
                //                    if (!file.getCharset().equals("UTF-8")) {
                //                      file.setCharset("UTF-8", monitor);
                //                    }

                jsonString = NodeMapper.toJSON(uuid, mapToList(old),true).toString(4);
  
                ByteArrayInputStream in = new ByteArrayInputStream(jsonString.getBytes("UTF-8"));
  
                if (!file.exists()){
                  if(!file.getParent().exists())
                    recCreate(file.getParent(), monitor);
                  file.create(in, IResource.FORCE, null);
                }
                else file.setContents(in, IResource.FORCE, monitor);
                if (file == null)
                  return;
                if (!file.getParent().exists())
                  return;
              } catch (JSONException e) {
                RLogger.logError(e);
                RLogger.logError(trace);
              } catch (CoreException e) {
                RLogger.logError(e);
                RLogger.logError(trace);
              } catch (UnsupportedEncodingException e) {
                RLogger.logError(e);
                RLogger.logError(trace);
              }
              finally{
                if(file != null && (!file.exists()||isEmpty(file))){
                  RLogger.logError("File "+file.getFullPath()+" is empty after save attrs. Trace:\n"+trace);
                }
              }
            }
          };
      
      ISchedulingRule rule = MultiRule.combine(
          ruleFactory.modifyRule(project), ruleFactory.modifyRule(getFile(uuid)));
      if(parf!=null)
        rule = MultiRule.combine(ruleFactory.modifyRule(parf),
            rule);
      runWithoutDeltaProc(
          saveAttributes, rule
          );
    }
    finally{
      wl.unlock();
    }
    //strictRunWithoutDeltaProcWithReact(pfoldercreate,saveAttributes,react, getFile(uuid).getParent());
    //strictRunWithoutDeltaProc(saveAttributes, getFile(uuid).getParent());
  }

  private IFile getFile(UUID uuid) {
    String id = getNodeId(uuid);
    if(id == null)
      return null;
    return getFile(uuid, getParent(uuid), id, false, null);
  }

  @Override
  public void moveNodeTo(final UUID curNodeUUId, final UUID newParentUUId, byte flag) {
    Lock wl = writeLock(null);//TODO null?
    IFile toFile = null;
    try{
      waitAccess(project);
      IFile jsonFile = getFile(curNodeUUId);
      UUID oldParentUUID = getParent(curNodeUUId);
      String parentId = getNodeId(newParentUUId);
      if (parentId.equals("/")) {
        parentId = "root";
      }
      String nodeId = getNodeId(curNodeUUId);
      try {
        toFile = getResource(newParentUUId,LockType.LOCK_WAIT)
            .getParent()
            .getFolder(Path.fromOSString(parentId))
            .getFile(Path.fromOSString(nodeId + ".json"));
      } catch (LockIsBusy e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      if(toFile == null)
        return;
  
      MoveNodeJob moveJob =
          new MoveNodeJob(this, toFile, curNodeUUId, oldParentUUID, newParentUUId, flag);
      moveJob.trace = CoreUtils.getTrace(Thread.currentThread().getStackTrace());
      moveJob.oprationsBefore();
      IWorkspace works = ResourcesPlugin.getWorkspace();
      IResourceRuleFactory ruleFactory = works.getRuleFactory();
      String name = jsonFile.getName();
      String sname = name.substring(0, name.length()-5);
      name = toFile.getName();
      String pname = name.substring(0, name.length()-5);
      List<ISchedulingRule> rules = new LinkedList<>();
      genMoveRule(jsonFile.getParent(),pname,toFile.getParent(),pname,ruleFactory,rules);
      runWithoutDeltaProc(
          moveJob,
          MultiRule.combine(
              rules.toArray(new ISchedulingRule[rules.size()])));
//      run(new IWorkspaceRunnable(){
//      
//        @Override
//        public void run(IProgressMonitor monitor) throws CoreException {
//          try {
//            
//          } catch (Exception e) {
//            e.printStackTrace();
//          }
//          IWorkspace works = ResourcesPlugin.getWorkspace();
//          works.checkpoint(false);
//        }
//      }, null);
       moveJob.oprationsAfter();
     }catch(Exception e) {
       RLogger.logError(e);       
     }
     finally{
       wl.unlock();
     }
  }
  private static String JSON_EXT = ".json";
  private void genMoveRule(IContainer parent, String name, IContainer parent2, String pname, IResourceRuleFactory factory, List<ISchedulingRule> rules) {
  rules.add(factory.createRule(parent2));
  IContainer parCon = parent2;
  rules.add(factory.createRule(parCon));
  rules.add(factory.moveRule(parent.getFile(new Path(name+JSON_EXT)), parCon.getFile(new Path(name+JSON_EXT))));
  IContainer resCon = parent.getFolder(new Path(name+RESOURCE_SUFFIX));
  if(resCon.exists()) 
    rules.add(factory.moveRule(resCon, parCon.getFolder(new Path(name+RESOURCE_SUFFIX))));
  IContainer chiCon = parent.getFolder(new Path(name));
  if(chiCon.exists()) {
    IContainer targCon = parCon.getFolder(new Path(name));
    try {
      for(IResource r:chiCon.members()) {
        String chiname = r.getName();
        if(chiname.endsWith(JSON_EXT)) {
            genMoveRule(chiCon, 
              chiname.substring(0, chiname.length()-JSON_EXT.length()), 
            parCon, name, factory, rules);
        }
      }
    } catch (CoreException e) {
      e.printStackTrace();
    }
    rules.add(factory.deleteRule(parent));
    rules.add(factory.createRule(parent2));
    rules.add(factory.moveRule(chiCon, targCon));
  }
  
  }

  private void recCreate(IContainer rt, IProgressMonitor monitor) throws CoreException {
    if(!rt.getProject().isAccessible())
      return;
    if (!rt.exists()) { 
      recCreate(rt.getParent(), monitor);
      if (rt instanceof IFolder) ((IFolder) rt).create(true, true, monitor);
    }
  }

  @Override
  public void setResourceContents(
      final UUID uuid, String resName, final InputStream in, Object data, byte flag) {
    try {
      IStorageDeltaListener del = getDeltaTarget();
      final String name = CoreUtils.URLEncode(resName);
      if(in == null)
        return;
        //TODO delete?
      if (in.available() > 0) {
        Lock wl = writeLock(uuid); 
        try{
          final IFolder folder = getResourceFolder(uuid);
          if (folder == null) {
            try {
              in.close();
            } catch (IOException e) {
              //  TODO Auto-generated catch block
              RLogger.logError(e);
            }
            return;
          }
          IWorkspaceRunnable setResourceContents =
              new IWorkspaceRunnable() {
                @Override
                public void run(IProgressMonitor monitor) throws CoreException {
                  wl.lock();
                  //long t = System.currentTimeMillis();
                  try
                  {
                    if (del != null) {
                      del.resourceChanged(uuid, resName, Moment.BEFORE, flag);
                    try {
                      //folder.refreshLocal(0, monitor);
                      if (!folder.exists()) {
                        recCreate(folder, monitor);
                        //folder.create(true, true, monitor);
                      }
                    } catch (CoreException ce) {
                      ce.printStackTrace();
                    }
                    //IFile file = folder.getFile(resName);
                    //while (folder.getProject().isAccessible()&&!folder.exists()) ;
                    java.io.File f =
                        new java.io.File(URI.create(folder.getLocationURI() + "/" + (name)));
                    //if(!f.exists()){
                    try {
                      f.getParentFile().mkdirs();
                      try {
                        if (!f.exists()) f.createNewFile();
                      }catch(IOException e) {
                        e.printStackTrace();
                      }
                      FileOutputStream fos = new FileOutputStream(f);
                      byte[] buf = new byte[32 * 1024];
                      //ReadableByteChannel f1 = Channels.newChannel(in);
                      //FileChannel f2 = fos.getChannel();
                      int len = 0;
                      while ((len = in.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                      }
                      fos.close();
                    } catch (IOException e) {
                      // TODO Auto-generated catch block
                      e.printStackTrace();
                    }
                    
                    //folder.refreshLocal(1, null);
                    IFile file = folder.getFile(resName);
                    if(!file.isSynchronized(0))
                      file.refreshLocal(0, null);
                    if (!file.getParent().exists()) return;
//                    if (file.exists())
//                      if (!file.getCharset().toUpperCase().equals("UTF-8")) {
//                        try {
//                          file.refreshLocal(0, monitor);
//                          file.setCharset("UTF-8", monitor);
//                        } catch (CoreException ce) {
//                          ce.printStackTrace();
//                        }
//                      }
                  }
                }
                finally{
                  try {
          in.close();
          } catch (IOException e) {
          // TODO Auto-generated catch block
           RLogger.logError(e);
          }
                  wl.unlock();
                }
                  //                        }
                  //                            f..create(in, IResource.FORCE, null);
                  //                        else `
                  //f.setContents(in, IResource.FORCE, monitor);
                  //long d = System.currentTimeMillis()-t;
                  //RLogger.logInfo("time "+d+" "+resName);
              };
          };
         IWorkspace works = ResourcesPlugin.getWorkspace();
         wl.unlock();
          runWithoutDeltaProc(setResourceContents, folder.getParent());
          wl.lock();
          run(new IWorkspaceRunnable(){
          
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {

              if (del != null)
                del.resourceChanged(uuid, resName, Moment.AFTER, flag);
              works.checkpoint(false);
            }
          }, null);
        }
        finally{
          wl.unlock();
        }
      }else {
        try {
        in.close();
        } catch (IOException e) {
          RLogger.logError(e);
        }
      }
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  long time;

  @Override
  public void deleteResource(final UUID uuid, String name, byte flag) {
    Lock wl = writeLock(uuid);
    try{
      IStorageDeltaListener dnt = getDeltaTarget();
      name = CoreUtils.URLEncode(name);
      final String resName = name;
      IWorkspaceRunnable deleteResource =
          new IWorkspaceRunnable() {
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {
              try{
                if (dnt != null)
                  dnt.resourceChanged(uuid, resName, Moment.BEFORE, flag);
                IFolder res_folder = getResourceFolder(uuid);
                if (res_folder != null && res_folder.exists()) {
                  res_folder.getFile(resName).delete(true, monitor);
                  if (res_folder.members().length == 0) {
                    res_folder.delete(true, monitor);
                  }
                }
              }finally{
                
              }
            }
          };
      waitAccess(project);
      IContainer rule = getResourceFolder(uuid);
      if (rule != null) rule = rule.getParent();      
      IWorkspace works = ResourcesPlugin.getWorkspace();
      IResourceRuleFactory ruleFactory = works.getRuleFactory();
      IFolder rf = getResourceFolder(uuid);
      if(rf!=null && rf.exists()) {//if there are no res folder then nothing to delete here
        runWithoutDeltaProc(
            deleteResource,
            MultiRule.combine(
                ruleFactory.deleteRule(getFile(uuid)),
                rf==null?
                    ruleFactory.modifyRule(project)
                :
                  MultiRule.combine(
                    ruleFactory.deleteRule(getResourceFolder(uuid)),
                    ruleFactory.modifyRule(project))));
      }else
        RLogger.logDebug("Call delete of resource on not loaded node! "+uuid);
      runWithoutDeltaProc(new IWorkspaceRunnable(){
      
        @Override
        public void run(IProgressMonitor monitor) throws CoreException {

          if (dnt != null)
            dnt.resourceChanged(uuid, resName, Moment.AFTER, flag);
          IWorkspace works = ResourcesPlugin.getWorkspace();
          works.checkpoint(false);
        }
      }, null);
    }
    finally{
      wl.unlock();
    }
  }

  private void waitAccess(IProject project) {
    Long l = System.currentTimeMillis();
    while (!project.isAccessible()) if (System.currentTimeMillis() - l > 200) return;
  }
  private static void waitAccess(IResource f) {
    Long l = System.currentTimeMillis();
    while (!f.isAccessible()||f.isReadOnly()) if (System.currentTimeMillis() - l > 5000) return;
  }

  @Override
  public void createStorage(String lname,String pname) {
    Lock wl = writeLock(null);
    try{
      IWorkspaceRunnable createStorage =
          new IWorkspaceRunnable() {
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {
              try{
                if (!project.exists()) project.create(monitor);
              
              }finally{
                //special case, nodes are created at bottom
              }
              project.open(monitor);
              IProjectDescription description = project.getDescription();
              String[] natures = description.getNatureIds();
              String[] newNatures = new String[natures.length + 1];
              System.arraycopy(natures, 0, newNatures, 0, natures.length);
              newNatures[natures.length] = ReqCatalogue.ID;
              description.setNatureIds(newNatures);
              project.setDescription(description, monitor);
              IWorkspace works = ResourcesPlugin.getWorkspace();
              works.checkpoint(false);
            }
          };
      IResource rule = ResourcesPlugin.getWorkspace().getRoot();
  
      IWorkspace works = ResourcesPlugin.getWorkspace();
      IResourceRuleFactory ruleFactory = works.getRuleFactory();
      waitAccess(project);
      runWithoutDeltaProc(createStorage, ruleFactory.createRule(rule));
    }
    finally{
      wl.unlock();
    }
  }

  @Override
  public void deleteStorage() {
    Lock wl = writeLock(null);
    try{
      IWorkspaceRunnable deleteStorage =
      
          new IWorkspaceRunnable() {
            @Override
            public void run(IProgressMonitor monitor) throws CoreException {
              try{
                project.delete(true, null);
                IWorkspace works = ResourcesPlugin.getWorkspace();
                works.checkpoint(false);
              }
              finally{
                
              }
            }
          };
      waitAccess(project);
      IWorkspace works = ResourcesPlugin.getWorkspace();
      IResourceRuleFactory ruleFactory = works.getRuleFactory();
      runWithoutDeltaProc(deleteStorage, ruleFactory.deleteRule(project));
    }
    finally{
      wl.unlock();
    }
  }

  private Map<IWorkspaceRunnable, ISchedulingRule> jbs =
      new HashMap<>();
  private LinkedList<IWorkspaceRunnable> jorder = new LinkedList<>();
  private void run(final IWorkspaceRunnable wjob, final ISchedulingRule rule){
    ISchedulingRule trule = rule,
        old = activeRules;
    if(activeRules!=null)
      if(rule != null)
        trule = MultiRule.combine(
          activeRules,
          rule
          );
      else
        trule = activeRules;
  
    IWorkspace works = ResourcesPlugin.getWorkspace();

    try {
      try {
        activeRules = trule;
        deltaProcessor.deactivate();
        if (works.isTreeLocked()){
          RLogger.logError("tree is locked");
          return;
        }
        if (forceNow) wjob.run(null);
        else works.run(wjob, trule, IWorkspace.AVOID_UPDATE, null);
      } finally {
        deltaProcessor.activate();
        if(old!=null)
          activeRules = old;
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  private void runWithoutDeltaProc(
      final IWorkspaceRunnable wjob, final ISchedulingRule rule) {
    //works.checkpoint(false);
//    if (chRow > 0) {
//      //jbs.put(job,rule);
//      //jorder.addLast(job);
//      return;
//    }
    boolean sync = true;
    if(activeThread == null){
      activeThread = Thread.currentThread();
      sync = false;
    }else
      if(activeThread == Thread.currentThread())
        sync = false;
    if(sync){
      synchronized (getThreadLock(activeThread)) {
        run(wjob, rule);
      }
    }else
      run(wjob, rule);
  }
//
//  @Override
//  public int stopChangeRow() {
//    int i = super.stopChangeRow();
//    if (chRow == 0 && !jbs.isEmpty()) {
//      final Map<IWorkspaceRunnable, ISchedulingRule> jbs2 =
//          new HashMap<>(jbs);
//      jbs.clear();
//      IWorkspace works = ResourcesPlugin.getWorkspace();
//      deltaProcessor.deactivate();
//      works.checkpoint(false);
//      try {
//        works.run(
//            new IWorkspaceRunnable() {
//              @Override
//              public void run(IProgressMonitor monitor) throws CoreException {
//                for (IWorkspaceRunnable j : jorder) j.run(monitor);
//              }
//            },
//            jbs2.get(jorder.iterator().next()) /*test it*/,
//            IWorkspace.AVOID_UPDATE,
//            null);
//      } catch (CoreException e) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//      } finally {
//        jorder.clear();
//        jbs.clear();
//        deltaProcessor.activate();
//      }
//    }
//    return i;
//  };
  private Map<Thread, Object> threadLocks = new ConcurrentHashMap<>();
  private Object getThreadLock(Thread thread) {
    Object l = threadLocks.get(thread);
    if(l == null) {
      synchronized (threadLocks) {
        l = threadLocks.get(thread);
        if(l == null) {
          l = activeThread.getName()+"_lock";
          threadLocks.put(thread, l);
        }
      }
    }
    return l;
  }

// //////////////////////////////////////////////////////////////////////////
  //
  // cache related methods
  //
  // //////////////////////////////////////////////////////////////////////////
  private boolean resinited = false;

  public ResourceMap getResourceMap() {
    return getCMM().getResourceMan();
  }

//  public IFile getResource(UUID uuid) {
//    return getResource(uuid, LockType.LOCK_WAIT);
//  }
  public IFile getResource(UUID uuid,LockType lock) throws LockIsBusy {
    java.nio.file.Path s = getResourcePath(uuid, lock);
    if(s == null)
      return null;
    return project.getFile(s.toString());//TODO test
  }

  @Override
  public File getJSONResource(UUID node, LockType lt) throws LockIsBusy {
    IFile f = getResource(node, lt);
    if(f == null)
      return null;
    return f.getLocation().toFile();
  }

  private java.nio.file.Path getResourcePath(UUID uuid, LockType lock) throws LockIsBusy {
    return getResourceMap().getResourcePath(uuid,lock);
  }

  private final Lock projectLock = new ReentrantLock();

  public boolean lockProject() {
    return projectLock.tryLock();
  }

  public void unLockProject() {
    projectLock.unlock();
  }

  @Override
  public ITreeStorage getBaseStorage() {
    // TODO Auto-generated method stub
    return null;
  } 
  public void clearProblems(){
    if(project.isOpen() && project.isAccessible() && !project.getWorkspace().isTreeLocked()){
      synchronized (getLockObject(project)) {//lock for markers
        try {
          project.deleteMarkers(EclipseCheckerContainer.REQUALITY_MARKER_WARN_TYPE, true, IResource.DEPTH_INFINITE);
          project.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
        } catch (CoreException e) {
          e.printStackTrace();
        }
      }
    }
  }
//  public void clearAll(boolean del) {
//    try {
//      if(!del)
//        clearProblems();
//      //getResourceMap().dropCache(project.getLocation().toFile().toPath(), getCMM().getSettingsStore());
//      
//      resinited = false;
//      super.clearCaches(del);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//  }
  @Override
  public void clearNodeCache(UUID uuId) {
    boolean b1=false;
    ResourceMap rm = null;
    Lock wl = writeLock(null);
    b1 = true;
    try{
      super.clearNodeCache(uuId);
      getVersionsStorage().remove(uuId);
      rm = getResourceMap();
      rm.writeLock();
      java.nio.file.Path res = null;
      try {
        res = getResourceMap().getResourcePath(uuId, LockType.NO_LOCK);
      } catch (LockIsBusy e) {
        e.printStackTrace();
      }
      clearPersistent(uuId);
      if (res != null) {
        getResourceMap().cacheRemove(res, -1);
      }
      activeAttributes.remove(uuId);
    }finally{
      if(b1)
        wl.unlock();
      if(rm!=null)
        rm.writeUnlock();
    }
    super.clearNodeCache(uuId);
    //resourceMap.clearCache();

    //resinited = false;
    //resourceMap = null;
  }
//
//  @Override
//  public String getCacheVar(UUID uuid, String section, String var) {
//    return cs.getCacheValue(uuid, section, var);
//    //return getPersistent(uuid, section, var);
//  }
//
//  @Override
//  public void setCacheVars(UUID uid, String section, Map<String, Map<String,Object>> ts) {
//    Map<String, Map<String,Object>> oldAttributes = getCacheVars(uid, section, ts.keySet());
//    HashSet<String> allS = new HashSet<>();
//    allS.addAll(ts.keySet());
//    if (oldAttributes != null) allS.addAll(oldAttributes.keySet());
//    //for(String s:allS)
//    if (getDeltaTarget() != null)
//      getDeltaTarget()
//          .cacheChanged(
//              uid,
//              section,
//              allS,
//              oldAttributes != null ? oldAttributes : null,
//              ts,
//              Moment.BEFORE,
//              true);
//    cs.setCacheMaps(uid, section, ts);
//    //setPersistents(uid, section, ts);
//    //for(String s:allS)
//    if (getDeltaTarget() != null)
//      getDeltaTarget()
//          .cacheChanged(
//              uid,
//              section,
//              allS,
//              oldAttributes != null ? oldAttributes : null,
//              ts,
//              Moment.AFTER,
//              true);
//  }
//
//  @Override
//  public Map<String, Map<String,Object>> getCacheVars(UUID uid, String section, Set<String> keySet) {
//    return cs.getCacheMaps(uid, section, keySet);
//  }
//
//  @Override
//  public void setCacheVar(UUID uuid, String section, String var, String value) {
//    String old = getCacheVar(uuid, section, var);
//    Set<String> s = new HashSet<>();
//    s.add(var);
//    Map<String, Map<String,Object>> ov = new HashMap<>(), nv = new HashMap<>();
//    if(old!=null){
//      ov.put(var, new HashMap<>());
//      ov.get(var).put("", old);
//    }
//    nv.put(var, new HashMap<>());
//    nv.get(var).put("", value);
//    if (getDeltaTarget() != null)
//      getDeltaTarget().cacheChanged(uuid, section, s, ov, nv, Moment.BEFORE, true);
//    
//    if (getDeltaTarget() != null)
//      getDeltaTarget().cacheChanged(uuid, section, s, ov, nv, Moment.AFTER, true);
//  }



  @Override
  public File getResource(UUID uuid, String resName) {
    IFolder resFolder = getResourceFolder(uuid);
    if (resFolder == null || !resFolder.exists()) return null;
    //if (!resFolder.isSynchronized(1)) {
//    try {
//      resFolder.refreshLocal(1, null);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
    //}
    File f = resFolder.getFile(resName).getLocation().toFile();
    if (f == null || !f.exists()) return null;
//    if (!f.isSynchronized(0))
//      try {
//        f.refreshLocal(0, null);
//      } catch (CoreException e) {
//        e.printStackTrace();
//      }
    return f;
  }

  @Override
  public URI getLocation() {
    if(!project.exists())
      return null;
    return project.getLocationURI();
  }

  public boolean fileAccessible(String s) {
    IResource f = getProject().findMember(s);
    if(f!=null) {
      if(!f.exists())
        System.out.println("file "+s+" not exist");
      else
        if(!f.isAccessible())
          System.out.println("file "+s+" not accessible");
    }else {
      System.out.println("file "+s+" is null");
      if(!getProject().exists())
        System.out.println("project not ex");
    }
    return f!=null && f.exists() && f.isAccessible();
  }

  @Override
  public boolean fileAccessible(java.nio.file.Path relativePath) {
    if(relativePath == null)
    {
      RLogger.logDebug("Attempt to check a file by null path");
      RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      return false;
    }
    IResource member = project.findMember(relativePath.toString());
    if(member == null)
    {
      RLogger.logDebug("Attempt to check a file for which a description cannot be get "+relativePath);
      RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      return false;
    }	
    return member.isAccessible();
  }

  @Override
  public java.nio.file.Path getFullPath(java.nio.file.Path relativePath) {
    return java.nio.file.Path.of(getProject().getLocation().toString()).resolve(relativePath);
  }
  
  @Override
  public void setRootNodeUUID(UUID uid) {
    rootNodeUUID = uid;
  }


  @Override
  public java.nio.file.Path getProjectRelativePath(java.nio.file.Path fullPath) {
    if(fullPath == null)
      return null;
    return java.nio.file.Path.of(getProject().getLocation().toString()).relativize(fullPath);
  }

  @Override
  public UUID getUUId(final java.nio.file.Path relPath, boolean b) {
    return getUUId(project.getFile(relPath.toString()),false, b);//TODO test
  }


}
