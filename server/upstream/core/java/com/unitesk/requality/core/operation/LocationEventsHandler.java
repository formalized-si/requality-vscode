package com.unitesk.requality.core.operation;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class LocationEventsHandler {

  public static void callUpdate(List<TreeNodeChangeEvent> retevents) {
	ConcurrentLinkedQueue<TreeNode> docLocUpdateLabel = new ConcurrentLinkedQueue<TreeNode>();
	retevents.stream().forEach(ce-> {
      switch(ce.getType()) {
        case CHANGE:{
        	if(ce.getMoment()==Moment.AFTER) {
        		ChangesSet<Attribute> attrs = (ChangesSet<Attribute>) ce.getArguments()[0];
        		TreeNode targ = ce.getTreeNodeTarget();
        		if(targ!=null) {
                  if(targ.getType().equals(Document.TYPE_NAME)) {
                    if(attrs.contains(TreeNode.ATTR_SHOWN_CHILDREN)) {
                      if(!docLocUpdateLabel.contains(targ))
                        docLocUpdateLabel.add(targ);
                    }
                  }
                  if(targ.getType().equals(Location.TYPE_NAME))
                    if(attrs.contains(Location.ATTR_MARKUP))
                    {
                      TreeNode doc = targ.getWorkParent();
                      // VSCODE-PATCH: a location of a document deleted in the same operation has no parent
                      if(doc != null && !docLocUpdateLabel.contains(doc))
                        docLocUpdateLabel.add(doc);
                    }
                  if(targ.getType().equals(Requirement.TYPE_NAME)){
                  	if(attrs.contains(Requirement.ATTR_LOCATIONS)){
                      if(!(targ instanceof Requirement))
                        return;
                  	  Requirement r = (Requirement) targ;
                  	  for(TreeNode node:r.getLocations(false)) {
                        TreeNode doc = node == null ? null : node.getWorkParent();
                        // VSCODE-PATCH: a location of a deleted document has no parent
                        if(doc != null && !docLocUpdateLabel.contains(doc))
                          docLocUpdateLabel.add(doc);
                  	  }
                  	}
                  }
        		}
        	}
        }break;
      }
    });
	ConcurrentLinkedQueue<Requirement> reqToUp = new ConcurrentLinkedQueue<Requirement>();
	for(TreeNode dup: docLocUpdateLabel) {
      // VSCODE-PATCH: a document deleted in the same operation has no locations to update
      if(dup.isDisposed())
        continue;
      for(TreeNode loc:dup.getActiveWorkChildren())
      {
    	  if(!(loc instanceof Location))
    	    continue;
    	  Location l = (Location) loc;
    	  for(Requirement r:l.getRequirements())
    	    if(r != null && !reqToUp.contains(r))
    		  reqToUp.add(r);
      }
    }
    for(Requirement r:reqToUp)
      r.updateLocations(r);
  }

}
