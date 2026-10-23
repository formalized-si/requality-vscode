package com.unitesk.requality.core.storage;

import java.nio.file.Path;
import java.util.UUID;

import com.unitesk.requality.core.storage.cache.UUIDsCacheMan;

public interface IUUIDsCachedTreeStorage extends ITreeStorage{

  public UUIDsCacheMan getUCM();

  public UUID getStorageUUId(Path path, boolean legacyMode);

  public UUID getUUId(Path path, boolean b);

  public void setRootNodeUUID(UUID uid);

  public boolean fileAccessible(Path string);

  public Path getFullPath(Path relativePath);

  public Path getProjectRelativePath(Path fullPath);

  public ResourceMap getResourceMap();
}
