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
package com.unitesk.requality.model;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ModelProvider;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public class RequalityModelProvider extends ModelProvider {
  public static final String ID = "com.unitesk.requality.model.RequalityModelProvider";

  public RequalityModelProvider() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public ResourceMapping[] getMappings(
      IResource resource, ResourceMappingContext context, IProgressMonitor monitor)
      throws CoreException {
    return super.getMappings(resource, context, monitor);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Object getAdapter(Class adapter) {
    return super.getAdapter(adapter);
  }

  @Override
  public ResourceMapping[] getMappings(
      IResource[] resources, ResourceMappingContext context, IProgressMonitor monitor)
      throws CoreException {
    return super.getMappings(resources, context, monitor);
  }

  @Override
  public ResourceTraversal[] getTraversals(
      ResourceMapping[] mappings, ResourceMappingContext context, IProgressMonitor monitor)
      throws CoreException {
    return super.getTraversals(mappings, context, monitor);
  }
}
