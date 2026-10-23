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

package com.unitesk.requality.core.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.ProgressRunnable;
import com.unitesk.requality.nodetype.ErrorNode;
import com.unitesk.requality.tool.CoreUtils;

public class FSStorage extends AbstractTreeStorage implements IUUIDsCachedTreeStorage {
  private UUID root = null;
  private static final String ROOT_NODE_FILENAME = "root.json";
  private static final Path ROOT_NODE_PATH = Path.of("root.json");
  private static final IResourceAble[] EMPTY = new IResourceAble[0];
//  private Map<UUID, File> nodeToFile = new HashMap<>();
  private Map<UUID, Map<String, AttributeDefinition>> activeAttributes = new HashMap<>();
  private final URI projectURI;
  private final Path projectFullPath;
  private ReentrantLock rl = new ReentrantLock();

//
  public ResourceMap getResourceMap() {
    return getCMM().getResourceMan();
  }

  public FSStorage(URI path, IProgressMonitor mon) {
    this.projectURI = path;
    projectFullPath = new File(path).toPath();
    if (mon == null)
      mon = monitor;
    try {
      initCaches(this, CacheInitializationStage.STORAGE_INIT, mon);
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }

  private UUID getUUIdByFullPath(Path file) {
    UUID uid = null;
    try {
      uid = getResourceMap().cacheGet(toRPath(file));
    } catch (LockIsBusy e) {
    }
    if (uid != null)
      return uid;
    return getStorageUUIdByFullPath(file);
  }

  //TODO remove
  private Map<Path, Path> paths = new HashMap<>();

  public Path getFullPath(UUID uuid) {
    Path realtivePath = getResourceMap().getResourcePath(uuid);
    if (realtivePath != null) {
      Path p = paths.get(realtivePath);
      if (p == null) {
        p = projectFullPath.resolve(realtivePath);
        paths.put(realtivePath, p);
      }
      return p;// TODO test
    }
    return null;
  }

  private static InputStream readFileByFullPath(Path fullPath) {
    if (fullPath == null)
      return null;
    if (!Files.exists(fullPath))
      return null;
    try {
      return Files.newInputStream(fullPath, StandardOpenOption.READ);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
    }
    return null;
  }

  @Override
  public UUID getRootNode() {
    // TODO make passive
    if (root != null)
      return root;
    Path filep = projectFullPath.resolve(ROOT_NODE_PATH);
    if (Files.exists(filep)) {
      UUID uuid = getUUIdByFullPath(filep);
      root = uuid;
      getResourceMap().cachePut(filep, ROOT_NODE_PATH, root, false, -1);// TODO test
      return uuid;
    }
    return null;
  }

  public File getResourceFolder(UUID uuid) {
    //TODO rewrite by path
    Path f = getFullPath(uuid);
    if (f == null)
      return null;
    File fl = f.toFile();
    if (fl == null || fl.getParent() == null)
      return null;
    String id = getNodeId(uuid);
    File resFolder = new File(fl.getParent() + File.separator + id + ResManager.RESOURCES_SUFFIX);
    return resFolder;
  }

  @Override
  public String getNodeId(UUID uuid) {
    if (uuid.equals(getRootNode())) {
      return "/";
    }
    Path nodeFullPath = getFullPath(uuid);
    if (nodeFullPath == null) {
      return null; // node not found
    }
    return CoreUtils.getFileName(nodeFullPath, false);
  }

  @Override
  public UUID getParent(UUID uuid) {
    Path nodeFullPath = getFullPath(uuid);
    if (nodeFullPath == null) {
      return null; // node not found
    }
    return getParentUUIDForFullJsonPath(nodeFullPath);
  }

  public UUID getParentUUIDForFullJsonPath(Path file) {
    if(file == null)
      return null;
    Path folderParent = file.getParent();
    if (folderParent == null || folderParent.resolve(ROOT_NODE_FILENAME).equals(file)) {
      return null;//ignore getParent calls on root.json
    }

    String folderName = CoreUtils.getFileName(folderParent, true);

    Path parentFolder = folderParent.getParent();
    Path parentResource = parentFolder.toAbsolutePath().resolve(folderName + ".json");

    return getUUIdByFullPath(parentResource);
  }

  private final UUID getFullPathUUID(Path one) {
    if (one == null)
      return null;
    Path rp = one;
    if (!Files.isDirectory(rp))
      if (rp.toString().toLowerCase().endsWith(".json")) {
        UUID childUUID = getUUIdByFullPath(rp);
        if (childUUID != null)
          return childUUID;
        else
          RLogger.logError("not loaded " + rp.toString());
        // nodeToFile.put(childUUID, res);
      }
    return null;
  }

  @Override
  public Collection<UUID> getChildren(UUID uuid) {
    Path fileP = getFullPath(uuid);
    if (fileP == null) {
      return Collections.emptySet(); // node not found
    }

    String nodeId = getNodeId(uuid);
    if (nodeId.equals("/")) {
      nodeId = "root";
    }
    Path nodeFolder = fileP.getParent();
    Path childrenFolder = nodeFolder.toAbsolutePath().resolve(nodeId);

    if (!Files.exists(childrenFolder)) {
      return Collections.emptySet();
    }
    try (Stream<Path> ds = Files.list(childrenFolder)) {
      return ds.parallel().map(one -> getFullPathUUID(one)).filter(one -> one != null).collect(Collectors.toList());
    } catch (IOException e) {
      RLogger.logError(e);
    }
    return Collections.emptySet();
  }

  @Override
  public void createStorageNode(NodeDescSet desca, String template, byte flag) {
    IStorageDeltaListener del = getDeltaTarget();
    if (del != null)
      del.beforeCreated(desca, template, flag);
    LinkedList<NodeDesc> nds = desca.allNodesCreate;
    ListIterator<NodeDesc> la = nds.listIterator();
    // TODO parallel?
    while (la.hasNext()) {
      NodeDesc desc = la.next();
      File file = null;
      FileOutputStream fos = null;
      UUID parentUUId = desc.getWorkParentUUID();
      UUID uuid = desc.getUUId();
      String id = desc.getId();
      Map<String, AttributeDefinition> attributes = desc.getAttributes();
      try {
        if (parentUUId == null) {
          assert uuid == root || root == null;
          file = new File(URI.create(projectURI + ROOT_NODE_FILENAME));
        } else {
          String parentId = getNodeId(parentUUId);
          if (parentId.equals("/")) {
            parentId = "root";
          }
          File childFolder = getFullPath(parentUUId).getParent().toAbsolutePath().resolve(parentId).toFile();
          //TODO replace by path
          if (!childFolder.exists()) {
            childFolder.mkdirs();
          }
          file = new File(childFolder.getAbsolutePath() + File.separator + id + ".json");
        }

        activeAttributes.put(uuid, new HashMap<>(attributes));
        if (!file.getParentFile().exists()) {
          file.getParentFile().mkdirs();
        }
        if (!file.exists()) {
          file.createNewFile();
        }

        String jsonString = NodeMapper.toJSON(uuid, attributes, true).toString(4);
        fos = new FileOutputStream(file);
        Writer w = new OutputStreamWriter(fos, "UTF-8");
        w.append(jsonString);
        w.flush();
        w.close();

        // update model
        java.nio.file.Path p1 = file.toPath();
        java.nio.file.Path pp = projectFullPath;

        getResourceMap().cachePut(file.toPath(), pp.relativize(p1), uuid, true, -1);

      } catch (UnsupportedEncodingException e) {
        RLogger.logError(e);
      } catch (JSONException e) {
        RLogger.logError(e);
      } catch (IOException e) {
        RLogger.logError(e);
      } finally {
        if (fos != null)
          try {
            fos.close();
          } catch (IOException e) {
            RLogger.logError(e);
          }
        if (file != null && (!file.exists() || isEmpty(file))) {
          RLogger.logError("File " + file.getPath() + " is empty after creation. Trace:\n"
              + CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
        }
      }
    }
    if (del != null)
      del.created(desca, template, flag);
  }

  @Override
  public void remove(NodeDescSet ndb, byte flag) {

    ResourceMap rm = getResourceMap();
    Path fullPath = null;
    rm.readLock();
    try {
      fullPath = getFullPath(ndb.root.getUUId());
    } finally {
      rm.readUnlock();
    }
//    if(ares==null || !ares.toFile().exists())
//      return;

    String fullPathName = fullPath == null ? null : CoreUtils.getFileName(fullPath.getFileName(), false);
    Path childsCont = null, resourcesCont = null;
    
    Path temp = fullPath == null ? null : fullPath.getParent().resolve(fullPathName);
    if (temp != null && Files.isDirectory(temp))
      childsCont = temp;
    temp = fullPath == null ? null : fullPath.getParent().resolve(fullPathName + ResManager.RESOURCES_SUFFIX);// TODO replace by
                                                  // const
    if (temp != null && Files.isDirectory(temp))
      resourcesCont = temp;

    StorageDeleteOperation<Path, Path> sdo = new StorageDeleteOperation<Path, Path>() {
      @Override
      public Object formSchelduleRule(
          java.util.concurrent.ConcurrentLinkedDeque<DeleteNodeResources<Path, Path>> deleteQueue) {
        return null;
      }

      @Override
      public void startDeleteJob(Object rule, ProgressRunnable pr) {
//          JobManager.jm.startBaseJob("remove some node", new RunnableWithMonitor() {
//      
//      @Override
//      public void run(IProgressMonitor pm) {
        pr.run(monitor);
//      }
//      }, true);
      }

      @Override
      public void startProjectScopedJob(String name, ProgressRunnable pr, Object project) {
//        JobManager.jm.startBaseJob("remove "+name+" in project "+project, new RunnableWithMonitor() {
//      
//      @Override
//      public void run(IProgressMonitor pm) {
        pr.run(monitor);
//      }
//      }, true);
      }
    };
    // TODO add to monitor?
    sdo.delete(getDeltaTarget(), rm, ndb, fullPath == null ? null : toFileAble(fullPath),
        childsCont == null ? null : toContainerAble(childsCont),
        resourcesCont == null ? null : toContainerAble(resourcesCont), new IContainerAbleRefresher() {

          @Override
          public void refreshRecoursive(IContainerAble cont) {
          }
        }, activeAttributes, this, flag);
  }

  private IContainerAble<Path> toContainerAble(Path childsCont) {
    if (childsCont == null)
      return null;
    return new IContainerAble<Path>() {

      @Override
      public Path getRaw() {
        return childsCont;
      }

      @Override
      public String getName() {
        return CoreUtils.getFileName(childsCont, true);
      }
      @Override
      public IContainerAble<Path> getParent() {
        Path par = childsCont.getParent();
        if(par!=null)//TODO check to be on project?
          return toContainerAble(par);
        return null;
      }

      @Override
      public void waitAccess() {
        FSStorage.waitAccess(childsCont);
      }

      @Override
      public void delete(boolean b, com.unitesk.requality.core.operation.IProgressMonitor monitor) {
        CoreUtils.delete(childsCont.toFile(), b, true);//TODO change to path
      }

      private IResourceAble[] childs = null;

      @Override
      public IResourceAble[] members() {
        if (childs == null) {
          try {
            try (Stream<Path> res = Files.list(childsCont)) {
              if (res == null) {//TODO rewrite cnt
                childs = EMPTY;
                return EMPTY;
              }
              Object[]resObjs = res.toArray();
              childs = new IResourceAble[resObjs.length];
              int i = 0;
              for (Object ro : resObjs) {
                Path r = (Path) ro;
                if (Files.isDirectory(r))
                  childs[i++] = toContainerAble(r);
                else
                  childs[i++] = toFileAble(r);
              }
              return childs;
            }
          } catch (IOException e) {
            RLogger.logError(e);
          }
        }
        return childs;
      }

    };
  }

  protected static void waitAccess(Path childsCont) {
    // TODO Auto-generated method stub

  }

  private IFileAble<Path, Path> toFileAble(Path ares) {
    if (ares == null)
      return null;
    return new IFileAble<Path, Path>() {

      @Override
      public Path getRaw() {
        return ares;
      }

      @Override
      public IContainerAble<Path> getParent() {
        return toContainerAble(ares.getParent());
      }

      @Override
      public String getName() {
        return CoreUtils.getFileName(ares, true);
      }

      @Override
      public Path toRelativePath() {
        try {
          return FSStorage.this.toRPath(ares);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        return null;
      }

      @Override
      public void waitAccess() {
        FSStorage.waitAccess(ares);
      }

      @Override
      public void delete(boolean b, com.unitesk.requality.core.operation.IProgressMonitor monitor) {
        CoreUtils.delete(ares.toFile(), b, true);//TODO replace by path
      }
    };
  }

  @Override
  public void changeNodeId(UUID node, String id, byte flag) {
    // TODO Auto-generated method stub

  }

  public Path toRelativePath(Path resource) {
    if (resource == null)
      return null;
    return toRPath(resource);
  }

  public Path toRPath(Path resource) {
    if (resource == null)
      return null;
    return projectFullPath.relativize(resource);
  }

  @Override
  public Map<String, AttributeDefinition> getStorageAttributes(UUID uuid) {
    try {
      if (activeAttributes.containsKey(uuid))
        return activeAttributes.get(uuid);
      InputStream is1 = readFileByFullPath(getFullPath(uuid));
      try {
        Map<String, AttributeDefinition> val = NodeMapper.getAttributes(uuid, is1,
            TempFolderFilesProvider.getOne(getResourceFolder(uuid), true));
        activeAttributes.put(uuid, val);
        return val;
      } finally {
        if (is1 != null)
          is1.close();
      }
    } catch (JSONException e) {
      RLogger.logError(e);
      return null;
    } catch (IOException e) {
      RLogger.logError(e);
      return null;
    }
  }

  @Override
  public void saveStorageAttributes(UUID node, ChangesSet<Attribute> changed, byte flag) {
    File file = null;
    try {
//      getDeltaTarget().changed(node, changed, attributes, Moment.BEFORE, true);
      // Map<String, Attribute> old = oldattributes;//getAttributes(node);
      Map<String, AttributeDefinition> oldMap = getAttributes(node);
      Map<String, Attribute> newA = changed.getNewMap();
      for (Entry<String, Attribute> en : newA.entrySet()) {
        Attribute a = en.getValue();
        if (oldMap == null)
          oldMap = getAttributes(node);
        if (oldMap == null)
          oldMap = new HashMap<>();
        if (a == null)
          oldMap.remove(en.getKey());
        else
          oldMap.put(en.getKey(), en.getValue().getDefinition());
      }
      if (oldMap == null || oldMap.isEmpty())
        activeAttributes.remove(node);
      else
        activeAttributes.put(node, oldMap);

      if (oldMap == null || oldMap.isEmpty()) {
        RLogger.logDebug("Attempted to save empty set of attrs for " + node, 2);
        return;
      }

      if (oldMap != null && oldMap.get(TreeNode.ATTR_TYPE) != null
          && oldMap.get(TreeNode.ATTR_TYPE).getRawValue().getValue().equals(ErrorNode.TYPE_NAME)) {
        RLogger.logDebug("Attempted to save corrupted node " + node, 2);
        return;// cannot save error node attrs
      }
      Path fullPath = getFullPath(node);
      if (fullPath == null)
        throw new RuntimeException("No file exist for uuid " + node);
      //TODO replace by path
      file = fullPath.toFile();
      JSONObject jo = NodeMapper.toJSON(node, oldMap, true);
      try {
        String jsonStrring = jo.toString(4);
        try (FileOutputStream fos = new FileOutputStream(file)) {
          try (OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8")) {
            out.append(jsonStrring);
          }
        }
      } finally {
        jo.close();
      }

    } catch (UnsupportedEncodingException e) {
      RLogger.logError(e);
    } catch (FileNotFoundException e) {
      RLogger.logError(e);
    } catch (IOException e) {
      RLogger.logError(e);
    } catch (JSONException e) {
      RLogger.logError(e);
    } finally {
      if (file != null && (!file.exists() || isEmpty(file))) {
        RLogger.logError("File " + file.getPath() + " is empty after save attrs. Trace:\n"
            + CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      }
    }
  }

  private static boolean isEmpty(File file) {
    InputStream is = null;
    try {
      is = Files.newInputStream(file.toPath(), StandardOpenOption.READ);
      return is.available() == 0;
    } catch (IOException e) {
      RLogger.logError(e);
    } finally {
      if (is != null)
        try {
          is.close();
        } catch (IOException e) {
          RLogger.logError(e);
        }
    }
    return true;
  }

  @Override
  public void moveNodeTo(UUID node, UUID parent, byte flag) {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean canLoad(UUID uuid) {
    Path relativePath = getResourceMap().getResourcePath(uuid);
    if (relativePath != null) {
      try {
        return Files.exists(projectFullPath.resolve(relativePath));
      } catch (Exception exc) {
        RLogger.logDebug(exc, 1);
      }
    }
    // TODO Check json
    return false;
  }

  @Override
  public URI getNodeResourcePath(UUID uuId) {
    File f = getResourceFolder(uuId);
    if (f != null)
      return f.toURI();
    return null;
  }

  @Override
  public InputStream getResourceContents(UUID uuid, String resName) {
    // TODO: stream reuse check
    try {
      URI path = getNodeResourcePath(uuid);
      if (path == null)
        return null;
      return new FileInputStream(new File(new URI(path.toString() + "/" + CoreUtils.URLEncode(resName))));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void setResourceContents(UUID uuid, String resName, InputStream in, Object data, byte flag) {
    try {
      if (getDeltaTarget() != null)
        getDeltaTarget().resourceChanged(uuid, resName, Moment.BEFORE, flag);
      try {
        URI path = getNodeResourcePath(uuid);
        if (path == null)
          return;
        File fol = new File(path);//TODO replace by path operations
        String[] path2 = resName.replace(File.separator, "/").split("/");
        for (int i = 0; i < path2.length - 1; i++) {
          fol = fol.toPath().resolve(path2[i]).toFile();
        }
        if (!fol.exists())
          fol.mkdirs();
        try (FileOutputStream out = new FileOutputStream(fol + File.separator + path2[path2.length - 1])) {
          byte[] buffer = new byte[1024];
          int len;
          while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
          }
        }
      }finally {
        if (getDeltaTarget() != null)
          getDeltaTarget().resourceChanged(uuid, resName, Moment.AFTER, flag);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        in.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public boolean isResourceExists(UUID uuid, String resName) {
    URI path = getNodeResourcePath(uuid);
    if (path == null)
      return false;
    try {
      return new File(new URI(path.toString() + "/" + CoreUtils.URLEncode(resName))).exists();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return false;
  }

  @Override
  public void deleteResource(UUID uuid, String resName, byte flag) {
    URI path = getNodeResourcePath(uuid);
    if (path == null)
      return;
    try {
      new File(new URI(path.toURL() + "/" + CoreUtils.URLEncode(resName))).delete();
    } catch (MalformedURLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void recursiveResourceList(File folder, String path, Set<String> result) {
    if (folder.exists())
      for (File f : folder.listFiles()) {
        // use unix path separator
        String curPath = (path.isEmpty() ? "" : (path + "/")) + f.getName();
        if (f.isDirectory()) {
          recursiveResourceList(f, curPath, result);
        } else {
          result.add(curPath);
        }
      }
  }

  @Override
  public Set<String> listResources(UUID uuid) {
    Set<String> result = new HashSet<>();
    File f = getResourceFolder(uuid);
    if (f != null)
      recursiveResourceList(f, "", result);
    return result;
  }

  @Override
  public String getProjectName() {
    return CoreUtils.getProjectName(new File(projectURI));
  }

  @Override
  public String getLocalName() {
    return new File(projectURI).getName();
  }

  @Override
  public String getVisibleName() {
    return new File(projectURI).getName();
  }

  @Override
  public void createStorage(String name, String pname) {
    new File(projectURI).getParentFile().mkdirs();
  }

  @Override
  public void deleteStorage() {
    // TODO Implement deleteStorage in FSStorage
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public ITreeStorage getBaseStorage() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public File getJSONResource(UUID uid, LockType lock) throws LockIsBusy {
    Path fullPath = getFullPath(uid);
    if (fullPath == null)
      return null;
    return fullPath.toFile();
  }

  @Override
  public File getResource(UUID uuid, String resName) {
    URI path = getNodeResourcePath(uuid);
    if (path == null)
      return null;
    try {
      return new File(new URI(path.toString() + "/" + CoreUtils.URLEncode(resName)));
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void clearNodeCache(UUID uid) {
    activeAttributes.remove(uid);
    super.clearNodeCache(uid);
  }

  @Override
  public void clearCaches(boolean del) {
    activeAttributes.clear();
    paths.clear();
    super.clearCaches(del);
  }

  @Override
  public URI getLocation() {
    if (!Files.exists(projectFullPath))
      return null;
    return projectURI;//TODO change to path?
  }

  public boolean fileAccessible(Path s) {
    return Files.exists(projectFullPath.resolve(s));
  }

  public boolean fileAccessible(File c) {
    // TODO test
    return fileAccessible(toRelativePath(c.toPath()));
  }

  @Override
  public void setRootNodeUUID(UUID uid) {
    root = uid;
  }

  @Override
  public Path getProjectRelativePath(Path fullPath) {
    if (fullPath == null)
      return null;//TODO check to be on path?
    return projectFullPath.relativize(fullPath);
  }

//  
  @Override
  public Path getFullPath(Path relativePath) {
    return projectFullPath.resolve(relativePath);
  }
  
  @Override
  public UUID getUUId(Path relativePath, boolean withCache) {
    if (!withCache)
      return getStorageUUIdByFullPath(getFullPath(relativePath));
    else
      return getUUIdByFullPath(getFullPath(relativePath));// TODO test
  }

  @Override
  public UUID getStorageUUId(Path relativePath, boolean b) {
    InputStream is = null;
    Path f = getFullPath(relativePath);
    if (f == null)
      return null;
    return getStorageUUIdByFullPath(f);
  }
  private static long readString, readDirect;
  private static int cnt = 0;
  public static UUID getStorageUUIdByFullPath(Path fullPath) {
    try {
      try {
        if (Files.size(fullPath) < 12000)
          return NodeMapper.getUUId(Files.readString(fullPath));
      }catch(IOException e) {
        RLogger.logError(e);
      }
      try(InputStream is = readFileByFullPath(fullPath)){
        return NodeMapper.getUUId(is);
      } catch (IOException e) {
        RLogger.logError(e);
      }
    } catch (JSONException e) {
      e.printStackTrace();
      return null;
    } finally {
    }
    return null;
  }

  private ConcurrentMap<UUID, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

  private ReentrantReadWriteLock getRWL(UUID uid) {
    ReentrantReadWriteLock rl = locks.get(uid);
    if (rl == null) {
      rl = new ReentrantReadWriteLock();
      locks.put(uid, rl);
    }
    return rl;
  }

  @Override
  public void writeUnlock(UUID uid) {
    if (uid == null)
      rl.unlock();
    else {
      getRWL(uid).writeLock().unlock();
    }
  }

  @Override
  public Lock writeLock(UUID uid) {
    if (uid == null) {
      rl.lock();
      return rl;
    } else {
      WriteLock wl = getRWL(uid).writeLock();
      wl.lock();
      return wl;
    }
  }

}
