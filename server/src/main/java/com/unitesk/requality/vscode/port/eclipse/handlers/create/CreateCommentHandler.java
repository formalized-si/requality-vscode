package com.unitesk.requality.vscode.port.eclipse.handlers.create;

import com.unitesk.requality.core.operation.create.CreateCommentOperation;
import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.vscode.port.eclipse.handlers.NewElementHandler;

public class CreateCommentHandler extends NewElementHandler {

  @Override
  protected CreateElementOperation getCreateElementOperation() {
    return CreateCommentOperation.getOne();
  }
}
