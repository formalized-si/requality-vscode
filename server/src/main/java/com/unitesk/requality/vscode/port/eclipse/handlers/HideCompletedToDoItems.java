package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.port.views.documents.ToDoList;

public class HideCompletedToDoItems extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent arg0) throws ExecutionException {

    boolean isPushed = ToDoList.isHideCompleted();
    ToDoList.setHideCompleted(!isPushed);

    return null;
  }
}
