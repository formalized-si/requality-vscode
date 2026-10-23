package com.unitesk.requality.core.storage.formatversion;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONException;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.storage.FSStorage;
import com.unitesk.requality.core.storage.NodeMapper;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.tool.CoreUtils;

public abstract class OldTo0213Convert {
  static String NODE_GROUP = "com.unitesk.nodegroup.requality";
  private HashMap<String, Object> processedFiles = new HashMap<>();

  abstract Map<String, AttributeDefinition> reworkAttrs(UUID uid, Map<String, AttributeDefinition> ad,
      Pair<Map<Path, UUID>, Map<UUID, Path>> uuidMaps, String version, boolean isRoot);

  public String convert(Path proj, String version) throws Exception {
    // TODO Auto-generated method stub
    try {
      // List<String> vers = new ArrayList<String>();
      Pair<Map<Path, UUID>, Map<UUID, Path>> uuidMaps = gatherUUIDMaps(proj, version, true);
      processContainer(proj, "", version, uuidMaps, true);
      // proj.refreshLocal(IResource.DEPTH_INFINITE, null);
    } catch (Exception e) {
      RLogger.logError(e);
      throw e;
    }
    return null;
  }

  Pair<Map<Path, UUID>, Map<UUID, Path>> gatherUUIDMaps(Path start, String version, boolean isRoot)
      throws IOException {
    Map<Path, UUID> fileToUUID = new ConcurrentHashMap<>();
    Map<UUID, Path> uUIDToFile = new ConcurrentHashMap<>();
    ConcurrentLinkedQueue<Path> toH = new ConcurrentLinkedQueue<>();
    toH.add(start);
    while (!toH.isEmpty()) {
      Path container = null;
      synchronized (fileToUUID) {
        container = toH.poll();
      }
      if (!Files.exists(container))
        continue;
      List<Path> allMembers = null;
      try (Stream<Path> members = Files.list(container)) {//TODO rewrite?
        if(members == null)
          continue;
        allMembers = members.collect(Collectors.toList());
      }
      if(allMembers == null)
        continue;
      try (Stream<Path> members = allMembers.stream()) {
        CountDownLatch membersLatch = new CountDownLatch(allMembers.size());
        members.parallel().allMatch(member -> {
          try {
            boolean isFile = !Files.isDirectory(member);
            String name = member.getFileName().toString();
            if (name.startsWith("."))
              return true;
            if (isFile) {
              RLogger.logDebug("Init processing file " + member.toAbsolutePath(), 8);
              if (name.toLowerCase().endsWith("json")) {
                UUID uid = null;
                try {
                  uid = FSStorage.getStorageUUIdByFullPath(member);
                } catch (Exception e) {
                  try {
                    uid = LegalicyNodeMapper
                        .getUUID(new String(Files.readAllBytes(member), StandardCharsets.UTF_8));
                  } catch (Exception r2) {
                    r2.printStackTrace();
                  }
                }
                if (uid != null) {
                  synchronized (fileToUUID) {
                    fileToUUID.put(member, uid);
                    uUIDToFile.put(uid, member);
                  }
                } else {
                  RLogger.logError("Cannot parse file uuid for " + member.toAbsolutePath());
                }
              }
              RLogger.logDebug("End processing file " + member.toAbsolutePath(), 8);
            } else if (!name.equals(".git") && (!name.equals(".reqcache")) && Files.isDirectory(member)) {
              synchronized (fileToUUID) {
                toH.add(member);
              }
            }
          }finally {
            membersLatch.countDown();
          }
          return true;
        });
        try {
          membersLatch.await();
        } catch (InterruptedException e1) {
           e1.printStackTrace();
        }
        if (isRoot)
          Files.list(container).filter(one -> one.getFileName().toString().equals("Instances"))
              .forEach(one -> {
                try {
                  recDelete(one);
                } catch (IOException e) {
                  e.printStackTrace();
                }
                RLogger.logDebug("Del folder " + one.toAbsolutePath(), 8);
              });
      }
    }
    return new Pair<Map<Path, UUID>, Map<UUID, Path>>(fileToUUID, uUIDToFile);
  }
  private ReentrantLock filesLock = new ReentrantLock();
  private List<Path> handledFiles = new LinkedList<>(); 
  void processContainer(Path container, String path, String version, Pair<Map<Path, UUID>, Map<UUID, Path>> uuidMaps,
      boolean isRoot) throws IOException {
    List<Path> allMembers = null;
    try (Stream<Path> members = Files.list(container)) {//TODO rewrite ?
      if(members == null)
        return;
      allMembers = members.collect(Collectors.toList());
    }
    if(allMembers == null)
      return;
    CountDownLatch cdl = new CountDownLatch(allMembers.size());
    try (Stream<Path> members = allMembers.stream()) {
      members.parallel().forEach(member -> {
        try {
          boolean isFile = !Files.isDirectory(member);
          String name = member.getFileName().toString();
          if (name.startsWith("."))
            return;
          if (isFile) {
            filesLock.lock();
            try {
              Path p = member.toAbsolutePath();
              if(handledFiles.contains(p))
                return;
              handledFiles.add(p);
            } finally {
              filesLock.unlock();
            }
            RLogger.logDebug("Init processing file " + path + "/" + name, 8);
            if (name.endsWith("json")) {
              
              try {
                handleFile(path + "/" + name, member, version, uuidMaps, isRoot);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
            RLogger.logDebug("End processing file " + path + "/" + name, 8);
          }
          return;
        }finally {
          cdl.countDown();
        }
      });
    }
    try {
      cdl.await();
    } catch (InterruptedException e1){
       e1.printStackTrace();
    }
    CountDownLatch cdl2 = new CountDownLatch(allMembers.size());
    try (Stream<Path> members = allMembers.stream()) {
      members.parallel().forEach(member -> {
        try {
          String name = member.getFileName().toString();
          if (!name.equals(".git") && (!isRoot || !name.equals(".reqcache")) && Files.isDirectory(member)) {
            RLogger.logDebug("Init processing folder " + path + "/" + name, 8);
            if (isRoot && name.equals("Instances")) {
              try {
                recDelete(member);
              } catch (IOException e) {
                e.printStackTrace();
              }
              RLogger.logDebug("Del folder " + path + "/" + name, 8);
              return;
            }
            try {
              processContainer(member, path + "/" + name, version, uuidMaps, false);
            } catch (IOException e) {
              RLogger.logError(e);
            }
            RLogger.logDebug("End processing folder " + path + "/" + name, 8);
          }
        }finally {
          cdl2.countDown();
        }
      });
    }
    try {
      cdl2.await();
    } catch (InterruptedException e1){
       e1.printStackTrace();
    }
  }

  private void recDelete(Path member) throws IOException {
    if (Files.isDirectory(member))
      Files.list(member).forEach(one -> {
        try {
          recDelete(one);
        } catch (IOException e) {
          RLogger.logError(e);
        }
      });
    Files.delete(member);
  }

  protected void handleFile(String path, Path f, String version, Pair<Map<Path, UUID>, Map<UUID, Path>> uuidMaps,
      boolean isRoot) throws IOException {
    UUID uid = uuidMaps.getFirst().get(f);
    // try to load initial info for json file
    String absPath = f.toAbsolutePath().toString();
    if (uid != null) {
      // InputStream file = null;
      try {
        Map<String, AttributeDefinition> map = loadDeclarativeAttributes(uid, f, version);
//          String filex = null;
//          try {
//            filex = new String(Files.readAllBytes(f.toPath()));
//          } catch (IOException e1) {
//            // TODO Auto-generated catch block
//            e1.printStackTrace();
//          }

        if (map == null || map.isEmpty())
          throw new RuntimeException("Empty attributes set for " + absPath);
        map = reworkAttrs(uid, map, uuidMaps, version, isRoot);
        if (map.isEmpty() || map.values().isEmpty())
          throw new RuntimeException("Empty attributes set after rework for " + absPath);
        String val = null;
        try {
          val = NodeMapper.toJSON(uid, map.values(), true).toString(4);
        } catch (com.unitesk.jsonclone.JSONException e) {
          RLogger.logError(e);
        }
        if (Files.exists(f) && val != null) {
          ByteArrayInputStream in = null;
          try {
            in = new ByteArrayInputStream(val.getBytes("UTF-8"));
            try {
              Files.copy(in, f, StandardCopyOption.REPLACE_EXISTING);
              WriteLock wl = processFilesLock.writeLock();
              wl.lock();
              try {
                processedFiles.put(absPath, f);
              } finally {
                wl.unlock();
              }
            } catch (IOException e) {
              RLogger.logError(e);
              throw e;
            }
//              f.refreshLocal(0, null);
//              f.setContents(in, true, true, null);
          } finally {
            if (in != null)
              try {
                in.close();
              } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
          }
        } else if (val != null) {
          ByteArrayInputStream in = null;
          try {
            in = new ByteArrayInputStream(val.getBytes("UTF-8"));
            Files.copy(in, f, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException re) {
            try {
              in = new ByteArrayInputStream(val.getBytes("UTF-8"));
              Files.copy(in, f, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException re2) {
              re2.printStackTrace();
            }
          } finally {
            if (in != null)
              try {
                in.close();
              } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
          }
        }
      } catch (JSONException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } finally {
      }
    }
  }

  private ReentrantReadWriteLock processFilesLock = new ReentrantReadWriteLock();

  protected Map<String, AttributeDefinition> loadDeclarativeAttributes(UUID uid, Path f, String version) {
    Map<String, AttributeDefinition> mret = null;
    boolean wasProcessed = false;
    ReadLock rl = processFilesLock.readLock();
    rl.lock();
    String absPath = f.toAbsolutePath().toString();
    try {
      if (processedFiles.containsKey(absPath))
        wasProcessed = true;
    } finally {
      rl.unlock();
    }
    java.io.File resFile = null;
    boolean resInited = false;
    if (!wasProcessed)
      try {
        if (!resInited) {
          resFile = getResFile(f);
          resInited = true;
        }
        Pair<String, Map<String, AttributeDefinition>> ret = LegalicyNodeMapper.getAttributes(uid,
            new IInputStreamProvider() {
              @Override
              public InputStream getStream() {
                return ResHelper.readFile(f);
              }
            }, TempFolderFilesProvider.getOne(resFile, true), version);
        mret = ret.getSecond();
      } catch (Exception e) {
        RLogger.logDebug(CoreUtils.getTrace(e) + "\n" + absPath + "\n" + f, 4);
      } finally {
        // file.reset();
      }
    if (mret == null || mret.isEmpty()) {
      if (!resInited) {
        resFile = getResFile(f);
        resInited = true;
      }
      try (InputStream file = ResHelper.readFile(f)) {
        mret = NodeMapper.getAttributes(uid, file, TempFolderFilesProvider.getOne(resFile, true));
      } catch (Exception e) {
        RLogger.logDebug(CoreUtils.getTrace(e) + "\n" + absPath + "\n" + f, 4);
      }
    }
    return mret;
  }

  private static File getResFile(Path f) {
    if (f == null)
      return null;
    String fname = f.getName(f.getNameCount() - 1).toString();
    fname = fname.substring(0, fname.length() - 5);
    File fa = f.getParent().resolve(fname + ResManager.RESOURCES_SUFFIX).toFile();
    if (fa.exists())
      return fa;
    return null;
  }
}
