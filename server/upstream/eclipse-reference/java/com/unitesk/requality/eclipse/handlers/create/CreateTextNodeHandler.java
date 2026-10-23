package com.unitesk.requality.eclipse.handlers.create;

import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.operation.create.CreateTextNodeOperation;
import com.unitesk.requality.eclipse.handlers.NewElementHandler;


public class CreateTextNodeHandler extends NewElementHandler {
  @Override
  protected CreateElementOperation getCreateElementOperation() {
    return CreateTextNodeOperation.getOne();
  }
}