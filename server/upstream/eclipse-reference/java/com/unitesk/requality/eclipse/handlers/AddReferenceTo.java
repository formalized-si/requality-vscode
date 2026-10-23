package com.unitesk.requality.eclipse.handlers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;

public class AddReferenceTo extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    TreeDB db = null;
    {
      for (final TreeNode node : nodes.fullSelection) {
        boolean started = false;
        if(db == null) {
          db = node.getTreeDB();
          started = db.startTransaction("Add reference to element" + (nodes.fullSelection.size() != 1 ? "s" : ""));
          if(!started)
            return null;
          
        }
        try {
          TreeNode rnode = node.getTreeDB().getLinkResolver().getRefTarget();
          if (rnode != null && node.getTreeDB().getLinkResolver().getRefTargetState(rnode) > 0) {
    
            node.attrs()
                .setAttribute(
                    new AttributeDefinition(node.getUUId(), 
                      node.getTreeDB().getDefReferenceName(),
                      new ReferenceValueProvider(
                          AttributeType.REFERENCE, rnode.getUserVisibleName(true)),
                      AttributeDefinition.detectFlags(node.getTreeDB().getDefReferenceName())));
            node.attrs().getLinkAttributeValue(node.getTreeDB().getDefReferenceName());
            node.commitAttributes();
            if (node.getTreeDB().getLinkResolver().getRefTargetState(rnode) == 1) {
              node.getTreeDB().getLinkResolver().setRefTarget(null);
              Map<String, Attribute> old = new HashMap<>();
              Attribute oa = old.put(
                  node.getTreeDB().getDefReferenceName(),
                  rnode.attrs().getAttribute(node.getTreeDB().getDefReferenceName()));
              ChangesSet<Attribute> set = new ChangesSet<>(Collections.emptySet(),AttributeStorage.formSet(node.getTreeDB().getDefReferenceName()),Collections.emptySet(),
                  AttributeStorage.formAttributeMap(oa),
                  AttributeStorage.formAttributeMap(old.get(node.getTreeDB().getDefReferenceName())),
                  AttributeStorage.formList(node.getTreeDB().getDefReferenceName()));
              
              node.getTreeDB()
                  .fireNodeGUIChange(
                      rnode,
                      set,
                      OFlag.NONE,
                      Moment.BEFORE);//TODO move upper
              node.getTreeDB()
                  .fireNodeGUIChange(
                      rnode,
                      set,
                      OFlag.NONE,
                      Moment.AFTER);
            }
          }
          if(started) {
            db.commit();
            started = false;
          }
        }finally {
          if(started)
            db.rollback();
        }
      }
    }
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (!super.isEnabled()) return false;
    TreeDB db = null;
    TreeStateManager tsm = null;
    for (TreeNode node : nodes.fullSelection) {
      if(db == null || db!=node.getTreeDB()) {
        db = node.getTreeDB();
        if(tsm == null)
          if(db != null)
            tsm = TreeStateManager.getOne(db.getTracker());
          else 
            tsm = TreeStateManager.getOne(ResManager.getManager().getTracker());
        if(db == null || !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
          return false;
      }
      if (!node.getTreeDB().equals(db) || !node.canEditAttributesInTable())
        return false;
    }
    if(db == null)
      return false;
    TreeNode rt = db.getLinkResolver().getRefTarget();
    return db.getLinkResolver().getRefTargetState(rt) > 0;
  }
}
