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
package com.unitesk.requality.vscode.port.model;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMappingContext;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceTraversal;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdapterFactory;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter;
import com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter2;

import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.tool.CoreUtils;

public class TreeNodeAdapterFactory implements IAdapterFactory {
  private Object requalityAdapter = new RequalityWorkbenchAdapter();

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Object getAdapter(final Object adaptableObject, Class adapterType) {
    if (adapterType.isInstance(adaptableObject)) {
      return adaptableObject;
    }
    if (adapterType.equals(ResourceMapping.class)) {
      final TreeNode node = (TreeNode) adaptableObject;
      if(node.isDisposed())
        return null;
      IFile ares = null;
      try {
        ares = (IFile) EclipseTracker.getResourceByNode(node, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        RLogger.logDebug(CoreUtils.getTrace(e),7);
      }
      if(ares == null)
        return null;
      //if (node.isDisposed()||res==null)
      //  return null;
      IFile res = ares;
      IProject p = res.getProject();
      ResourceMapping mapper =
          new ResourceMapping() {
            @Override
            public ResourceTraversal[] getTraversals(
                ResourceMappingContext context, IProgressMonitor monitor) throws CoreException {

              List<ResourceTraversal> result = new ArrayList<>();
              // Node
              if (res != null)
                result.add(
                    new ResourceTraversal(
                        new IResource[] {res}, IResource.DEPTH_ZERO, IContainer.EXCLUDE_DERIVED));
              if (res != null && res.getParent() != null) {
                IFolder resources =
                    res.getParent().getFolder(Path.fromOSString(node.getId() + ResManager.RESOURCES_SUFFIX));
                if (resources != null && resources.exists()) {
                  result.add(
                      new ResourceTraversal(
                          new IResource[] {resources},
                          IResource.DEPTH_INFINITE,
                          IContainer.EXCLUDE_DERIVED));
                }
                String id = node.getId();
                if(node.isRoot())
                  id = "root";
                IFolder children = res.getParent().getFolder(Path.fromOSString(id));
                if (children != null && children.exists()) {
                  result.add(
                      new ResourceTraversal(
                          new IResource[] {children},
                          IResource.DEPTH_INFINITE,
                          IContainer.EXCLUDE_DERIVED));
                }
              }

              return result.toArray(new ResourceTraversal[0]);
            }

            @Override
            public IProject[] getProjects() {
              if (p != null) return new IProject[] {p};
              return new IProject[0];
            }

            @Override
            public String getModelProviderId() {
              return RequalityModelProvider.ID;
            }

            @Override
            public Object getModelObject() {
              return node;
            }
          };
      return mapper;
    }
    if (adapterType == IWorkbenchAdapter.class || adapterType == IWorkbenchAdapter2.class
    // || adapterType == IWorkbenchAdapter3.class
    ) {
      return requalityAdapter;
    }
    if (adapterType.isAssignableFrom(IResource.class)) {
      try {
        return EclipseTracker.getResourceByNode((TreeNode) adaptableObject, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        e.printStackTrace();
      }
    }
    if (adapterType.equals(IFile.class)) {
      try {
        return EclipseTracker.getResourceByNode((TreeNode) adaptableObject, LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Class[] getAdapterList() {
    return new Class[] {
      ResourceMapping.class,
      IWorkbenchAdapter.class,
      IWorkbenchAdapter2.class,
      // IWorkbenchAdapter3.class,
      IResource.class
    };
  }
}
