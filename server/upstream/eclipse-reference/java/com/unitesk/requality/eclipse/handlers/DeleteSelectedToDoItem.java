package com.unitesk.requality.eclipse.handlers;

import java.util.Map;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.views.documents.ToDoList;

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
