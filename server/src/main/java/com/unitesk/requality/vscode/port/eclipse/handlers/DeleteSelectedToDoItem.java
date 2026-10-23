package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.Map;
import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.commands.IElementUpdater;
import com.unitesk.requality.vscode.ui.commands.UIElement;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.vscode.port.views.documents.ToDoList;

public class DeleteSelectedToDoItem extends AbstractHandler implements IHandler, IElementUpdater {

  @SuppressWarnings("rawtypes")
  @Override
  public void updateElement(UIElement arg0, Map arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  public Object execute(ExecutionEvent arg0) throws ExecutionException {

    ToDoItem item = ToDoList.getSelectedNode();
    if (item != null) {
      boolean started = item.getTreeDB().startTransaction("Deleting update processor task item");
      if (started) {
        try {
          item.delete();
          if(started) {
            started = false;
            item.getTreeDB().commit();
          }
        }finally {
          if(started)
            item.getTreeDB().rollback();  
        }
      }
    }
    return null;
  }
}
