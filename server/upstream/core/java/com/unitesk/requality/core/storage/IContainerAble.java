package com.unitesk.requality.core.storage;

public interface IContainerAble<T> extends IResourceAble{
  T getRaw();
  IContainerAble<T> getParent();
  IResourceAble[] members();

}
