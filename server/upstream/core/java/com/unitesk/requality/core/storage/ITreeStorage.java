package com.unitesk.requality.core.storage;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.UUIDsCacheMan;

public interface ITreeStorage {
  /***
   * Adds tree storage event listener
   * @param deltaProcessor event listener
   */
  public abstract void setDeltaTarget(IStorageDeltaListener deltaProcessor);
  /***
   * Get tree storage event listener
   * @return event listener
   */
  public abstract IStorageDeltaListener getDeltaTarget();
  /***
   * Add a new node to tree
   * @param parentNode parent of node to be add
   * @param node UUID of new node
   * @param type type of new node
   * @param id text id of new node
   */
  public abstract void createNode(UUID parentNode, UUID node, String type, String id, String template, byte flag);
  /***
   * Get type of node
   * @param uuid UUID of node
   * @return type of node
   */
  public abstract String getNodeRawType(UUID uuid);
  /***
   * Get UUID of root node of tree
   * @return UUID of root node
   */
  public abstract UUID getRootNode();
  /***
   * Get UUID of parent element of node with given UUID in tree
   * @param uuid UUID of node
   * @return UUID of parent node
   */
  public abstract UUID getParent(UUID uuid);
  /***
   * Get list of children UUIDs for given node in tree 
   * @param uuid UUID of node
   * @return list of children UUIDs 
   */
  public abstract Collection<UUID> getChildren(UUID uuid);
  /***
   * Add a new node to tree 
 * @param internal 
   * @param parentNode parent of node to be add
   * @param node UUID of new node
   * @param id text id of new node
   * @param attributes set of attributes in form of Key->Attribute
   */
  public abstract void createNode(
      NodeDescSet desc, String template, byte flag);
  /***
   * Remove attribute with given UUID
   * @param uuid UUID of node to be remove
   * @param collection 
   */
  public abstract void remove(NodeDescSet desc, byte flag);
  /***
   * Get id of node with given UUUID
   * @param uuid UUID of node
   * @return string identifier
   */
  public abstract String getNodeId(UUID uuid);
  /***
   * Change id of node
   * @param uuid UUID of node
   * @param id string identifier
 * @param internal 
   */
  public abstract void changeNodeId(UUID uuid, String id, byte flag);
  /***
   * Get stored attributes of node with given UUID 
   * @param uuid UUID of node
   * @return map of attributes in form of Key->Attribute
   */
  public abstract Map<String, AttributeDefinition> getAttributes(UUID uuid);
  /***
   * Save attributes for node with given UUID
   * Attributes are taken from New map of ChangesSet
   * @param uuid UUID of node
   * @param changed set of changed attributes
   */
  public abstract void saveAttributes(
      UUID uuid, ChangesSet<Attribute> changed, byte flag);
  /***
   * Get base storage. For all non virual storages should return null
   * @return base storage if any. Null otherwise
   */
  public abstract ITreeStorage getBaseStorage();
  /***
   * Get versions objects related to node with given UUID
   * @param uuid UUID of node
   * @return map of versions in form of key->Version
   */
  public abstract Map<String, Version> getVersions(UUID uuid);
  /***
   * Save versions for given node. Versions from New map of changes set will be taken
   * @param uuid UUID of node
   * @param changes set of changed versions
   */
  public abstract void saveVersions(UUID uuid,ChangesSet<Version> changes, byte flag);
  /***
   * Move node with given UUID to new parent
   * @param uuid UUID of node
   * @param parent UUID of new parent
   * @param internal TODO
   */
  public abstract void moveNodeTo(UUID uuid, UUID parent, byte flag);
  /***
   * Method to check if node with given UUID can be loaded from this storage
   * @param uuid UUID of node
   * @return true if node with given UUID can be loaded
   */
  public abstract boolean canLoad(UUID uuid);
  /***
   * Method to check if some resource exists on node with given UUID
   * @param uuid UUID of node
   * @param resName name of resource to check
   * @return true if resource with given name exists
   */
  public abstract boolean isResourceExists(UUID uuid, String resName);
  /***
   * Get path for node with given UUID where resources are located
   * @param uuid UUID of node
   * @return path where resources can be located
   */
  public abstract URI getNodeResourcePath(UUID uuid);
  public URI getExternalNodeResourcePath(TreeDB db, UUID uuid);
  /***
   * Get content of resource from node with given UUID if it exists
   * @param uuid UUID of node
   * @param resName name of resource
   * @return InputStream if resource exists and accessible
   */
  public abstract InputStream getResourceContents(UUID uuid, String resName);
  /***
   * Set content of resource on node with given UUID. Can be supplemented by additional data used only on transaction storage
   * @param uuid UUID of node
   * @param resName name of resource
   * @param in new content of resource
   * @param data additional data
   */
  public abstract void setResourceContents(UUID uuid, String resName, InputStream in, Object data, byte flag);
  /***
   * List names of resources from node with given UUID 
   * @param uuid UUID of node
   * @return set of resources' names
   */
  public abstract Set<String> listResources(UUID uuid);
  /***
   * Delete resource with given name from node
   * @param uuid UUID of node
   * @param resName name of resource
   */
  public abstract void deleteResource(UUID uuid, String resName, byte flag);
  /***
   * Method to be called during new storage creation. Tends to be storage-specific
   * @param name name of future storage
   */
  public abstract void createStorage(String localname, String projectname);
  /***
   * Delete storage
   */
  public abstract void deleteStorage();
  /***
   * Get name - string identifier of storage. 
   * @return name of storage
   */
  public abstract String getProjectName();
  /***
   * Get name - local string identifier of storage. 
   * @return name of storage
   */
  public abstract String getLocalName();
  public abstract String getVisibleName();
//  /***
//   * Start row of changes
//   * @return
//   */
//  public abstract int startChangeRow();
//  /***
//   * Stops row of changes
//   * @return
//   */
//  public abstract int stopChangeRow();
  /***
   * Get the ancestor of node in baseStorage row that has no base storage. For non-virtual storage should return itself.
   * @return root storage
   */
  public abstract ITreeStorage getRootStorage();
  /***
   * Clear inner caches for node with given UUID
   * @param uid UUID of node
   */
  public abstract void clearNodeCache(UUID uid);
  /***
   * Clear all inner caches
   */
  /**
   * Clear all related caches for storage
   * Warning! Should not be called directly if the storage will be used lately
   * @param del delete related cache resources
   */
  abstract void clearCaches(boolean del);
  /***
   * Get resource from node with given UUID in form of File
   * @param uuid UUID of node
   * @param name name of resource
   * @return file related to resource with given name
   */
  public abstract File getResource(UUID uuid, String name);
  
  public abstract void markResourcesOutOfSync();
  public abstract void markResourcesSync();
  public abstract boolean getResourcesOutOfSync();
  /***
   * Get the object related to cache data manipulations. Should be not null and return the same object of all storage lifecycle
   * @return cache storage
   */
  public abstract CacheStorage getCacheStorage();
  /***
   * Get the object related to cache data manipulations. Should be not null and return the same object of all storage lifecycle
   * @return cache storage
   */
  public abstract CacheStorage getCacheStorage(String s);
  /***
   * Get location of storage if any. Contains path for on-disk storages
   * @return location of storage
   */
  public abstract URI getLocation();
  
  public abstract Lock writeLock(UUID uid);
  public abstract void writeUnlock(UUID uid);
  public abstract UUIDsCacheMan getUCM();
  public abstract StorageCacheManipulatorsManager getCMM();
  public abstract Pair<String, ITreeStorage> startOperation(String string);
  public abstract String endOperation(String opId);
  public IProgressMonitor getMonitor();
  public void setMonitor(IProgressMonitor mon);
  public File getJSONResource(UUID uid, LockType lock) throws LockIsBusy;
  public abstract void initCaches(ITreeStorage storage, CacheInitializationStage stage, IProgressMonitor mon) throws Exception;
  public abstract void renew();
  public abstract Collection<IMark> getStorageNodeProblems(UUID uid);
}
