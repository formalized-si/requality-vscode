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

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.ResourceMap;

public class MoveNodeJob implements IWorkspaceRunnable {
  private IFile toFile;
  private UUID curNodeUUId;
  private UUID oldParentUUID;
  private UUID newParentUUId;
  private byte flag;
  private ResourceStorage rs;
  public String trace;

  public MoveNodeJob(
      ResourceStorage rs, IFile toFile, UUID curNodeUUId, UUID oldParentUUID, UUID newParentUUId,byte flag) {
    this.rs = rs;
    this.toFile = toFile;
    this.curNodeUUId = curNodeUUId;
    this.oldParentUUID = oldParentUUID;
    this.newParentUUId = newParentUUId;
    this.flag = flag;
  }

  private String getNodeId(IFile file) {
    if (!file.getFileExtension().equals("json")) {
      throw new IllegalArgumentException("File must be .json");
    }
    return file.getName().substring(0, file.getName().length() - 5);
  }
  String id = null;
  IFile fromFile = null;
  java.nio.file.Path nodechildfrom = null;
  IFolder nodeChildrenFolder;
  IPath oldChildrenLocation;
  IPath newChildrenLocation;
  boolean parentChanges = false;
  public void oprationsBefore() {
    try {
      fromFile = rs.getResource(curNodeUUId, LockType.NO_LOCK);
    } catch (LockIsBusy e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if(fromFile == null){
      RLogger.logError("Trying to move non existing file "+curNodeUUId);
      return;//
    }
    id = rs.getNodeId(curNodeUUId);
    
    rs.getDeltaTarget()
      .moved(curNodeUUId, oldParentUUID, newParentUUId, Moment.BEFORE, flag);
      
    parentChanges = !oldParentUUID.equals(newParentUUId);
    if(parentChanges){
        nodechildfrom = ResourceStorage.toRelativePath(fromFile.getParent()).resolve(id);
        nodeChildrenFolder = fromFile.getParent().getFolder(new Path(getNodeId(toFile)));
        oldChildrenLocation = nodeChildrenFolder.getLocation();
        try {
          if(nodeChildrenFolder.exists() && nodeChildrenFolder.members().length>0){
            IFolder newFolder = (IFolder) toFile.getParent();
            IFolder folder = newFolder.getFolder(Path.fromOSString(getNodeId(fromFile)));
            newChildrenLocation = folder.getLocation();
          }
        } catch (CoreException e) {
          RLogger.logError(e);
        }
    }
    ResourceMap rm = rs.getResourceMap();
    rm.writeLock();
    try{      
      if (parentChanges && (newChildrenLocation != null || oldChildrenLocation != null)) {
        rs.getResourceMap()
          .updatePath(//TODO test
              nodechildfrom,
              ResourceStorage.toRelativePath(toFile.getParent()).resolve(id));
      }
      rm.cacheReplace(ResourceStorage.toRelativePath(fromFile), ResourceStorage.toRelativePath(toFile), curNodeUUId);
    }finally{
      rm.writeUnlock();
    }

  }
  public void oprationsAfter() {
    rs.getDeltaTarget().moved(curNodeUUId, oldParentUUID, newParentUUId, Moment.AFTER, flag);
  }
  @Override
  public void run(IProgressMonitor monitor) throws CoreException {
    rs.getResourceMap().writeLock();
    try{
      IFolder newFolder = (IFolder) toFile.getParent();
      if (!newFolder.exists()) {
        newFolder.create(true, true, null);
      }
  
      if (parentChanges) {
        IFolder oldNodeFolder = (IFolder) fromFile.getParent();
        if (toFile.exists()) {
          try(InputStream is = fromFile.getContents()){
            toFile.setContents(is, 0, null);
            fromFile.delete(true, null);
          } catch (IOException e) {
            RLogger.logError(e);
		  }
        } else fromFile.move(toFile.getFullPath(), true, null);
        
        if (nodeChildrenFolder.exists()) {
          if (nodeChildrenFolder.members().length > 0) {
            IFolder folder = newFolder.getFolder(Path.fromOSString(getNodeId(fromFile)));
            IPath path = folder.getFullPath();
            try {
              nodeChildrenFolder.move(path, IResource.FORCE, null);
            } catch (Exception e) {
  
            }
          } else {
            nodeChildrenFolder.delete(IResource.FORCE, null);
          }
        }
        IFolder resFolder =
            fromFile
                .getParent()
                .getFolder(new Path(getNodeId(toFile) + ResourceStorage.RESOURCE_SUFFIX));
  
        if (resFolder.exists()) {
          if (resFolder.members().length > 0) {
            IPath path =
                newFolder
                    .getFolder(
                        Path.fromOSString(getNodeId(fromFile) + ResourceStorage.RESOURCE_SUFFIX))
                    .getFullPath();
            resFolder.move(path, true, null);
          } else {
            resFolder.delete(true, null);
          }
        }
        if (oldNodeFolder.members().length == 0) {
          oldNodeFolder.delete(true, null);
        }
      }
    }
    finally{
      rs.getResourceMap().writeUnlock();
      if(toFile!=null && (!toFile.exists() || ResourceStorage.isEmpty(toFile))){
        RLogger.logError("File "+toFile.getFullPath()+" is empty after move operation. Trace:\n"+trace);
      }
    }
  }
}
