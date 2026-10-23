package com.unitesk.requality.core.storage;

public interface IFileDeleteListener {
  public void fileWillBeDeleted(java.nio.file.Path f);
  public void fileWasDeleted(java.nio.file.Path f);
}
