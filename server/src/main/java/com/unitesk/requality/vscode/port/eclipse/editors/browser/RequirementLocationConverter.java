package com.unitesk.requality.vscode.port.eclipse.editors.browser;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionConverter;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.port.eclipse.editors.EditorUtils;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.Const;

public class RequirementLocationConverter implements ISelectionConverter{
  boolean changeStored = false;
  private static RequirementLocationConverter oneNot = new RequirementLocationConverter(false);
  private static RequirementLocationConverter oneChange = new RequirementLocationConverter(true);
  public RequirementLocationConverter(boolean alterStored){
    changeStored = alterStored;
  }
  @Override
  public Pair<Boolean,SelectionRecord> convertSelection(FSId fsida, SelectionRecord sel,  SelectionRecord oldTargSel) {
    boolean nolink = Const.NO_EXPLORER_EDITOR_LINK && !EditorUtils.LINKED_PROVIDER.isLinked();
    Map<String, TreeNode[]> ret = null;
    if(!SelectionManager.getOne().isMarkup(fsida) && nolink) {
      return new Pair<>(false,oldTargSel);
    }else
      if(nolink) 
        return new Pair<>(false, oldTargSel);
    boolean wasReq = sel.selectionByType.containsKey(Requirement.TYPE_NAME)||sel.selectionByType.containsKey(TextNode.TYPE_NAME);
    if(wasReq) {
      ret = new LinkedHashMap<>();
      int locc = 0;
      Map<TreeNode, Collection<TreeNode>> repl = null;
      repl = addToRepl(repl, sel.getByType(Requirement.TYPE_NAME));
      repl = addToRepl(repl, sel.getByType(TextNode.TYPE_NAME));
      if(repl==null || repl.isEmpty())
        return new Pair<>(changeStored, sel);
      sel = new SelectionRecord(sel);
      sel.replaceAll(repl); 
    }
    return new Pair<>(changeStored, sel);
  }
  private Map<TreeNode, Collection<TreeNode>> addToRepl(Map<TreeNode, Collection<TreeNode>> repl, Collection<TreeNode> nodes) {
    for(TreeNode req : nodes)
    {
      List<Location> l = ((Requirement)req).getLocations(true);
      if(l!=null && l.size()>0){
        if(repl == null)
          repl = new HashMap<>();
        repl.put(req, new LinkedList<>(l));
      }
    }
    return repl;
  }
  public static RequirementLocationConverter getOne(boolean alter){
    return alter?oneChange:oneNot;
  }
}
