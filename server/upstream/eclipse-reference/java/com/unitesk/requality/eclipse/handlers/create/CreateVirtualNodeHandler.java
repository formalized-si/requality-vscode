package com.unitesk.requality.eclipse.handlers.create;

import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.operation.create.CreateVirtualNodeOperation;
import com.unitesk.requality.eclipse.handlers.NewElementHandler;

public class CreateVirtualNodeHandler extends NewElementHandler {
  @Override
  protected CreateElementOperation getCreateElementOperation() {
    return new CreateVirtualNodeOperation();
  }
}
