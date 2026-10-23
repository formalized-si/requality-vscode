package com.unitesk.requality.vscode.port.eclipse.handlers.create;

import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.operation.create.CreateNodeTypeOperation;
import com.unitesk.requality.vscode.port.eclipse.handlers.NewElementHandler;

public class CreateNodeTypeHandler extends NewElementHandler {

  @Override
  protected CreateElementOperation getCreateElementOperation() {
    return CreateNodeTypeOperation.getOne();
  }
}
