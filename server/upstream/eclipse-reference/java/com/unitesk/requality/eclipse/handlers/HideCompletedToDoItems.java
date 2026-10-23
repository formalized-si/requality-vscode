package com.unitesk.requality.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import com.unitesk.requality.views.documents.ToDoList;

public class HideCompletedToDoItems extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent arg0) throws ExecutionException {

    boolean isPushed = ToDoList.isHideCompleted();
    ToDoList.setHideCompleted(!isPushed);

    return null;
  }
}
