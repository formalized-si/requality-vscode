package com.unitesk.requality.core.storage;

import com.unitesk.requality.core.operation.IProgressMonitor;

public interface IResourceAble {
  String getName();
  void waitAccess();
  void delete(boolean b, IProgressMonitor monitor);
}
