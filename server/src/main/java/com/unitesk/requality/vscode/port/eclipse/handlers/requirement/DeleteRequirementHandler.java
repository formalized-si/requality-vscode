/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.vscode.port.eclipse.handlers.requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;

public class DeleteRequirementHandler extends AbstractHandler implements IDeleteHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    return null;
  }

  public void recUpdateProcessed(TreeNode node, List<UUID> processed) {
    if (node == null) return;
    processed.add(node.getUUId());
//    for (UUID uid : node.getWorkChildrenUUIDs(true)) {
//      TreeNode nde = node.getTreeDB().getNode(uid);
//      recUpdateProcessed(nde, processed);
//    }
  }
  //    private void deleteLocations(Map<Requirement, Set<Location>> locs, List<UUID> processed) {
  //        for(Requirement req:locs.keySet())
  //	    	for (Location loc : locs.get(req)) {
  //	            if(processed.contains(loc.getUUId()))
  //	                continue;
  //	            List<UUID>toprocess = new ArrayList<UUID>();
  //	            recUpdateProcessed(loc, toprocess);
  //	            processed.addAll(toprocess);
  //	            //TODO: save jsData for undo/redo
  //	        }
  //        CommonRemoveLocation.RemoveLocations(locs, null, true);
  //    }

  @Override
  public boolean isSuitableFor(TreeNode tn) {
    return Requirement.TYPE_NAME.equals(tn.getType()) || TextNode.TYPE_NAME.equals(tn.getType());
  }

  @Override
  public void process(TreeNode[] nodes, List<UUID> processed, byte flag)
      throws ExecutionException {
    for (TreeNode node : nodes) {
      if (node == null)
        continue;
      if (processed.contains(node.getUUId())) continue;
      if (!(node.getWorkParent() instanceof Requirement)
          && !(node.getWorkParent() instanceof VirtualNode)) continue;
      TreeDB db = node.getTreeDB();
      if (!db.getTreeLogic().canBeDeleted(node)) continue;

      //collectLocations((Requirement)node, collected);
    }
    //        if(!collected.isEmpty()){
    //        	deleteLocations(collected, processed);
    //        }
    for (TreeNode node : nodes){
      if(node == null)
        continue;
      if (!processed.contains(node.getUUId())) {
        List<UUID> toprocess = new ArrayList<>();
        recUpdateProcessed(node, toprocess);
        node.delete();
        processed.addAll(toprocess);
      }
    }
  }
  //    private void collectLocations(Requirement req,
  //			Map<Requirement,Set<Location>> collected) {
  //    	if(!collected.containsKey(req))
  //    		collected.put(req, new HashSet<Location>());
  //    	for (Location loc : req.getLocations())
  //    		collected.get(req).add(loc);
  //        for (int i = 0; i < req.sizeWorkChildren(); i++) {
  //            TreeNode node = req.getWorkChild(i);
  //            if (node instanceof Requirement) {
  //                Requirement r = (Requirement) node;
  //                collectLocations(r, collected);
  //            }
  //        }
  //
  //	}
  private static String ReqLocProblem = "Selected Requirement (one or more) contains Locations.";

  @Override
  public String[] getWarnings(TreeNode[] nodes) {
    for (TreeNode node : nodes) {
      if(node == null)
        continue;
      if (isSuitableFor(node))
        if (((Requirement) node).getLocationQIds().size() > 0) return new String[] {ReqLocProblem};
    }
    return null;
  }

  @Override
  public String titleOverride(TreeNode[] nodes) {
    return null;
  }
}
