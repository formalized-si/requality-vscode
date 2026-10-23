package com.unitesk.requality.vscode.ui.ide.model;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IAdapterFactory;
import com.unitesk.requality.vscode.ui.workbench.IPersistableElement;

/** {@code org.eclipse.ui.ide.ResourceAdapterFactory}: adapts the resources to {@link IPersistableElement}. */
public class ResourceAdapterFactory implements IAdapterFactory {
  @Override
  public Object getAdapter(Object adaptableObject, @SuppressWarnings("rawtypes") Class adapterType) {
    if (adaptableObject instanceof IResource && adapterType == IPersistableElement.class) {
      return new ResourcePersistable((IResource) adaptableObject);
    }
    return null;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Class[] getAdapterList() {
    return new Class[] { IPersistableElement.class };
  }
}
