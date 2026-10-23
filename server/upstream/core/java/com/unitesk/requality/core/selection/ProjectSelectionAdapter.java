package com.unitesk.requality.core.selection;

import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.IResTracker;

public class ProjectSelectionAdapter implements ISelectionAdapter{
  private static ProjectSelectionAdapter one = new ProjectSelectionAdapter();
  public static ProjectSelectionAdapter getOne() {
    return one;
  }
  @Override
  public Pair<SelectionRecord, SelectionRecord> adaptChange(FSId selectionSource, SelectionRecord visRecord, SelectionRecord oldVisRecord, SelectionRecord storedSel, SelectionRecord oldStoredSel){
    return new Pair<>(adaptChange(selectionSource, visRecord, oldVisRecord),
                       adaptChange(selectionSource, storedSel, oldStoredSel));
  }
  
  public SelectionRecord adaptChange(FSId selectionSource, SelectionRecord record, SelectionRecord oldRecord) {
    if(record == null)
      return null;
    if(!record.fullSelection.isEmpty() && record.fullSelectionObjects.isEmpty())
    {
      IResTracker et = null;
      List<Object> projs = new LinkedList<>();
      for(TreeNode node:record.fullSelection)
        if(node.isRoot()) {
          if(et == null) 
            et = node.getTreeDB().getTracker();
          projs.add(et.getStoringEntity(node.getTreeDB()));
        }
      if(projs.size()>0) {
        Object[] objs = projs.toArray(new Object[projs.size()]);
        TreeNode[] nodes = record.fullSelection.toArray(new TreeNode[record.fullSelection.size()]);
        return new SelectionRecord(objs, nodes);
      }
    }
    return record;
  }

}
