package com.unitesk.requality.eclipse.editors.browser;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionConverter;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.editors.EditorUtils;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.Const;

public class LocationRequirementConverter implements ISelectionConverter{
  private static LocationRequirementConverter oneWatchingStored = new LocationRequirementConverter(true, true);
  private static LocationRequirementConverter oneNotWatchingStored = new LocationRequirementConverter(false, true);
  private static LocationRequirementConverter oneWatchingNotStored = new LocationRequirementConverter(true, false);
  private static LocationRequirementConverter oneNotWatchingNotStored = new LocationRequirementConverter(false, false);
  private boolean watchLink, changeStored;
  public LocationRequirementConverter(boolean watch, boolean changeStored) {
    watchLink = watch;
    this.changeStored = changeStored;
  }
  @Override
  public Pair<Boolean,SelectionRecord> convertSelection(FSId fsida, SelectionRecord sel, SelectionRecord oldTargSel) {
    boolean nolink = Const.NO_EXPLORER_EDITOR_LINK && !EditorUtils.LINKED_PROVIDER.isLinked();
    if(nolink && watchLink) {
      return new Pair<>(false, oldTargSel);
    }
    return new Pair<>(changeStored, convertMapSelection(sel));
  }
  public static SelectionRecord convertMapSelection(SelectionRecord sel) {
    SelectionRecord ret = sel;
    if(sel.getByType(Location.TYPE_NAME).size()>0) {
      Map <TreeNode, Collection<TreeNode>> repl = null;
      for(TreeNode node: sel.getByType(Location.TYPE_NAME)) {
        Location loc = (Location) node;
        Requirement[] r = ((Location) loc).getRequirements();
        if(r!=null) {
          int cn = 0;
          if(r.length > 0) 
          {
            if(repl == null)
              repl = new HashMap<>();
            repl.put(node, Arrays.asList(r));
          }
        }
      }
      if(repl!=null) {
        SelectionRecord rets = new SelectionRecord(sel);
        rets.replaceAll(repl);
        return rets;
      }
    }
    return sel;
  }
  public static LocationRequirementConverter getOne(boolean watchLink, boolean changeStored){
    if(changeStored)
      if(watchLink)
        return oneWatchingStored;
      else
        return oneNotWatchingStored;
    if(watchLink)
      return oneWatchingNotStored;
    else
     return oneNotWatchingNotStored;
    
  }
}
