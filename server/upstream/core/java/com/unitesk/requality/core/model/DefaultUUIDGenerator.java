package com.unitesk.requality.core.model;

import java.util.UUID;

public class DefaultUUIDGenerator implements IUUIDGenerator {

  @Override
  public synchronized UUID nextUUId() {
    return UUID.randomUUID();
  }
}
