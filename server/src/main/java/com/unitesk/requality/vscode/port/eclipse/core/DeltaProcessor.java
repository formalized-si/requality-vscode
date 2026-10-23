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
package com.unitesk.requality.vscode.port.eclipse.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeEvent;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeListener;
import com.unitesk.requality.vscode.ui.resources.IResourceDelta;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.ResManager;

public class DeltaProcessor implements IResourceChangeListener {
  private ResourceStorage rs;
  private Map<UUID, IFile> added = new LinkedHashMap<>();
  private Map<UUID, IFile> deleted = new LinkedHashMap<>();
  private Map<UUID, IFile> changed = new LinkedHashMap<>();
  private Map<UUID, IFile[]> moved = new LinkedHashMap<>();
  private boolean isActive = true;

  public DeltaProcessor(ResourceStorage rs) {
    this.rs = rs;
  }

  private boolean isChanging = false;
  @Override
  public void resourceChanged(IResourceChangeEvent event) {
	if(event.getType() == IResourceChangeEvent.PRE_DELETE && event.getResource()!=null && 
			event.getResource() instanceof IProject) {
				
		return;
	}
    if (!isActive) {
      return;
    }
    if (event.getType() == IResourceChangeEvent.PRE_DELETE
        || event.getType() == IResourceChangeEvent.PRE_CLOSE) {
      return;
    }
    if (event.getDelta()!=null && event.getDelta().getResource().getType() == IResource.ROOT) {

      isChanging = true;
      List<IResourceDelta> delta = new LinkedList<>();
      for (IResourceDelta rd : event.getDelta().getAffectedChildren()) {
        if (rd.getResource().getProject().equals(rs.getProject())) {
          delta.add(rd);
        }
      }
      processDelta(delta);
      isChanging = false;
    } else {
      throw new IllegalArgumentException("Delta resource must be root type");
    }
  }

  private void processChange(IFile file, int changeType) {
    if (!isActive) {
      return;
    }
    UUID uuid;
    switch (changeType) {
      case IResourceDelta.ADDED:
        if (!rs.inCache(file)) {
          uuid = rs.getUUId(file, false);
          if (uuid != null) added.put(uuid, file);
        }
        break;
      case IResourceDelta.REMOVED:
        try {
          uuid = rs.getUUId(file, false);
          if (uuid != null) deleted.put(uuid, file);
        } catch (Exception e) {
          RLogger.logError(e);
          //TODO why? resource not exists?
          //e.printStackTrace();
        }
        break;
      case IResourceDelta.CHANGED:
        uuid = rs.getUUId(file, false);
        if (uuid != null) changed.put(uuid, file);
        break;
      default:
        throw new RuntimeException();
    }
  }

  public void processDelta(final List<IResourceDelta> delta) {
    if (!isActive) {
      return;
    }
    final ResourceStorage rs = this.rs;
    final Map<UUID, IFile> changed = this.changed;
    int inter = 0;
    for(IResourceDelta d:delta)
    	if(isInteresing(d))
    		inter++;
    if(inter == 0)
    	return;
    WorkspaceJob j =
        new WorkspaceJob("process site delta") {
          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
            IStorageDeltaListener delt = rs.getDeltaTarget();
            synchronized (changed) {
              //					try{
              //						delt.startChangeRow();
              //						moved.clear();
              //				        deleted.clear();
              //				        added.clear();
              //				        changed.clear();
              //
              //				        Map<IFile, Integer> result = collectChanges(delta, null);
              //				        for (IFile f : result.keySet()) {
              //				            processChange(f, result.get(f));
              //				        }
              //
              //				        findNodeMove();
              //						for (UUID uuid : changed.keySet()) {
              //				        	if (changed.get(uuid).exists()) {
              //				        		rs.getDeltaTarget().changed(uuid, null,new HashMap<String,Attribute>(rs.getAttributes(uuid)),Moment.BEFORE, false);
              //								rs.getDeltaTarget().changed(uuid, null,null,Moment.AFTER, false);
              //				            }
              //				        }
              //				        // apply changes to cache
              //				        for (UUID uuid : deleted.keySet()) {
              //				        	rs.getDeltaTarget().deleted(uuid,Moment.BEFORE, false);
              //				            rs.getResourceMap().cacheRemove(deleted.get(uuid),rs.getProject());
              //				            rs.getDeltaTarget().deleted(uuid,Moment.AFTER,false);
              //				        }
              //
              //				        for (UUID uuid : added.keySet()) {
              //				            rs.getDeltaTarget().beforeCreated(uuid, rs.getParent(uuid),rs.getAttributes(uuid), false);
              //				            rs.getDeltaTarget().created(uuid, false);
              //				        }
              //
              //				        for (UUID uuid : moved.keySet()) {
              //				            IFile to = moved.get(uuid)[0];
              //				            IFile from = moved.get(uuid)[1];
              //				            if (from.getParent().equals(to.getParent())) { // rename move
              //				               	rs.getResourceMap().cacheReplace(from, to, uuid,rs.getProject());
              //				               	rs.getDeltaTarget().changed(uuid,null,null,Moment.BEFORE, false);
              //				               	rs.getDeltaTarget().changed(uuid,null,null,Moment.AFTER, false);
              //				            } else {
              //				                String opId = from.getParent().getName();
              //				                IFile oldParentFile = from.getParent().getParent()
              //				                    	.getFile(new Path(opId + ".json"));
              //				                UUID fromParent = getUUIdFromMoved(oldParentFile);
              //				                if (fromParent == null) {
              //				                    fromParent = rs.getUUId(oldParentFile);
              //				                }
              //				                rs.getDeltaTarget().moved(uuid, fromParent, rs.getParent(to),Moment.BEFORE, false);
              //				                rs.getResourceMap().cacheReplace(from, to, uuid,rs.getProject());
              //				   	            rs.getDeltaTarget().moved(uuid, fromParent, rs.getParent(to),Moment.AFTER, false);
              //				            }
              //				        }
              //					}finally{
              //						delt.stopChangeRow();
              //					}
            }
            return Status.OK_STATUS;
          }

          private String getContent(IFile iFile) throws IOException, CoreException {
            InputStream is = iFile.getContents();
            if (is == null) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            while (br.ready()) sb.append(br.readLine());
            br.close();
            return sb.toString();
          }
        };
    j.setRule(ResourcesPlugin.getWorkspace().getRoot());
    //j.schedule();
    try {
      j.runInWorkspace(null);
    } catch (CoreException e) {
      e.printStackTrace();
    }
  }

  private boolean isInteresing(IResourceDelta d) {
	if(d.getResource() instanceof IProject) {
		return d.getMovedFromPath()!=null || d.getMovedToPath()!=null;
	}
	return false;
  }

  private void findNodeMove() {
    if (!isActive) {
      return;
    }
    for (UUID uuid : deleted.keySet()) {
      if (added.get(uuid) != null) {
        moved.put(uuid, new IFile[] {added.get(uuid), deleted.get(uuid)});
      }
    }

    for (UUID uuid : moved.keySet()) {
      added.remove(uuid);
      deleted.remove(uuid);
    }

    Map<UUID, IFile[]> result = new HashMap<>();

    for (UUID uuid : moved.keySet()) {
      IFile[] f = moved.get(uuid);
      if (!(isAbsorbed(f[0]) || isAbsorbed(f[1]))) {
        result.put(uuid, f);
      }

      rs.getResourceMap().cacheReplace(ResourceStorage.toRelativePath(f[1]), ResourceStorage.toRelativePath(f[0]), uuid);
    }
    moved = result;
  }

  private boolean isAbsorbed(IFile f) {
    for (UUID uuid : moved.keySet()) {
      IFile chFiles[] = moved.get(uuid);
      String chFileNameZero = chFiles[0].getName().substring(0, chFiles[0].getName().length() - 5);
      String chFileNameOne = chFiles[1].getName().substring(0, chFiles[1].getName().length() - 5);

      IPath f1p =
          chFiles[0].getParent().getFullPath().addTrailingSeparator().append(chFileNameZero);
      IPath f2p = chFiles[1].getParent().getFullPath().addTrailingSeparator().append(chFileNameOne);

      if (f1p.isPrefixOf(f.getFullPath())) {
        if (!f.equals(chFiles[0])) {
          return true;
        }
      }
      if (f2p.isPrefixOf(f.getFullPath())) {
        if (!f.equals(chFiles[1])) {
          return true;
        }
        return true;
      }
    }
    return false;
  }

  public UUID getUUIdFromMoved(IFile file) {
    for (UUID uuid : moved.keySet()) {
      if (moved.get(uuid)[0].equals(file)) {
        return uuid;
      }
      if (moved.get(uuid)[1].equals(file)) {
        return uuid;
      }
    }
    return null;
  }

  private boolean isNode(IResource resource) {
    if (rs.getResourceMap().inCache(ResourceStorage.toRelativePath(resource))) {
      return true;
    }
    if (resource.getType() == IResource.FILE) {
      IFile file = (IFile) resource;
      if (file.getFileExtension() != null && file.getFileExtension().equals("json")) {
        return true;
      }
    }
    return false;
  }

  private Map<IFile, Integer> collectChanges(IResourceDelta delta, Map<IFile, Integer> changes) {
    Map<IFile, Integer> result = changes;
    if (result == null) {
      result = new LinkedHashMap<>();
    }

    IResourceDelta subDeltas[] = delta.getAffectedChildren();

    for (IResourceDelta sub : subDeltas) {
      IResource res = sub.getResource();
      if ((sub.getKind() & IResourceDelta.ADDED) != 0) {
        UUID nodeUUID;
        if (isNode(res)) {
          result.put((IFile) res, IResourceDelta.ADDED);
        } else if ((nodeUUID = getAffectedNode(res)) != null) {
          IFile file = (IFile) res;
          result.put(file, IResourceDelta.CHANGED);
        }
      }

      if ((sub.getKind() & IResourceDelta.CHANGED) != 0
          && (sub.getFlags() & IResourceDelta.MARKERS) == 0) {
        if (sub.getAffectedChildren().length == 0) {
          UUID nodeUUID = getAffectedNode(res);
          if (nodeUUID != null) {
            IResource dres = res;
            result.put((IFile) dres, IResourceDelta.CHANGED);
          }
        }
      }

      collectChanges(sub, result);

      if ((sub.getKind() & IResourceDelta.REMOVED) != 0) {
        UUID nodeUUID;
        if ((nodeUUID = getAffectedNode(res)) != null) {
          IFile file = (IFile) res;
          if (result.get(file) == null) {
            result.put(file, IResourceDelta.REMOVED);
          }
        }
        if (isNode(res)) {
          result.put((IFile) res, IResourceDelta.REMOVED);
        }
      }
    }
    return result;
  }

  private UUID getAffectedNode(final IResource res) {
    if (isNode(res)) {
      if (res.exists()) {
        UUID uuid = rs.getUUId((IFile) res, false);
        if (uuid != null) {
          return uuid;
        } else {
          WorkspaceJob job =
              new WorkspaceJob("Add marker") {
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                  try {
                    IMarker marker = res.createMarker(IMarker.PROBLEM);
                    marker.setAttribute(IMarker.MESSAGE, "element missing");
                    return Status.OK_STATUS;
                  } catch (CoreException e) {
                    e.printStackTrace();
                    return Status.CANCEL_STATUS;
                  }
                }
              };
          job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().markerRule(res));
          job.schedule();
          return null;
        }
      } else {
        return null;
      }
    }

    UUID uuid = rs.getRootNode();

    if (uuid == null) {
      return null; // new project
    }

    IResource root = null;
    try {
      root = rs.getResource(uuid, LockType.NO_LOCK);
    } catch (LockIsBusy e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if (root == null) return null; //TODO look into
    IPath path =
        res.getLocation().removeFirstSegments(root.getParent().getLocation().segmentCount() + 1);

    for (String segment : path.segments()) {
      if (segment.endsWith(ResManager.RESOURCES_SUFFIX)) {
        String nodeName = segment.substring(0, segment.length() - ResManager.RESOURCES_SUFFIX.length());
        UUID result = findChild(rs, uuid, nodeName);
        if (result != null) {
          return result;
        }
      }
      uuid = findChild(rs, uuid, segment);
      if (uuid == null) {
        return null;
      }
    }
    return uuid;
  }

  private static UUID findChild(ITreeStorage store, UUID puuid, String id) {
    Collection<UUID> children = store.getChildren(puuid);
    for (UUID child : children) {
      if (store.getNodeId(child).equals(id)) {
        return child;
      }
    }
    return null;
  }

  public void activate() {
    isActive = true;
  }

  public void deactivate() {
    isActive = false;
  }

  public boolean isActive() {
    return isActive;
  }
}
