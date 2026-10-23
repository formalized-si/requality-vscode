package com.unitesk.requality.core.storage;

import java.nio.file.Path;

public interface IFileAble<F, Fld> extends IResourceAble{
  F getRaw();
  IContainerAble<Fld> getParent();
  Path toRelativePath();
}
