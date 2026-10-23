package com.unitesk.requality.core.storage;

import java.nio.file.Path;
import java.util.UUID;

public class DeleteNodeResources<F,Fld> {
  public IContainerAble<Fld> parent;
  public UUID uid;
  public IFileAble<F, Fld> json;
  public IContainerAble<Fld> children;
  public IContainerAble<Fld> resources;
  public Path filePath;
  public DeleteNodeResources(IContainerAble<Fld> par, UUID uid, IFileAble<F, Fld> jsonFile, IContainerAble<Fld> childrenCont, IContainerAble<Fld> resourcesCont, Path filePath) {
    this.parent = par;
    this.uid = uid;
    this.json = jsonFile;
    this.children = childrenCont;
    this.resources = resourcesCont;
    this.filePath = filePath;
  }
  public int getResourcesCount() {
    return 1 + (children!=null?1:0) + (resources!=null?1:0);
  }
}