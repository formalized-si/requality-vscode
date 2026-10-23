package com.unitesk.requality.core.api;

import com.unitesk.requality.core.operation.mass.AbstractNodeOperation;

public class MassOppDescriptor {
  private String id, name;
  private AbstractNodeOperation op;
  private boolean debug;

  public MassOppDescriptor(String i, String n, AbstractNodeOperation op, boolean debug) {
    id = i;
    name = n;
    this.op = op;
    this.debug = debug;
  }

  public AbstractNodeOperation getOp() {
    return op;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isDebug() {
    return debug;
  }
}
