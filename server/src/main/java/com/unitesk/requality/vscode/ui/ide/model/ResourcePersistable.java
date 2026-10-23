package com.unitesk.requality.vscode.ui.ide.model;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IPersistableElement;

/** The persistable form of a resource (the IDE's resource adapter): its path and type, restored by {@link ResourceFactory}. */
public final class ResourcePersistable implements IPersistableElement {
  private final IResource resource;

  public ResourcePersistable(IResource resource) {
    this.resource = resource;
  }

  @Override
  public String getFactoryId() {
    return ResourceFactory.ID;
  }

  @Override
  public void saveState(IMemento memento) {
    memento.putString("path", resource.getFullPath().toOSString());
    memento.putInteger("type", resource.getType());
  }
}
